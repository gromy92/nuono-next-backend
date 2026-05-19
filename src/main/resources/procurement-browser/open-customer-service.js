(() => {
  function visible(node) {
    if (!node) return false;
    const rect = node.getBoundingClientRect();
    return rect.width > 0 && rect.height > 0;
  }

  function cssPath(node) {
    if (!node || !node.tagName) return 'unknown';
    const parts = [];
    let current = node;
    while (current && current.nodeType === 1 && parts.length < 6) {
      let part = current.tagName.toLowerCase();
      if (current.id) {
        part += '#' + current.id;
        parts.unshift(part);
        break;
      }
      const className = (current.className || '').toString().trim().split(/\s+/).filter(Boolean).slice(0, 3).join('.');
      if (className) {
        part += '.' + className;
      }
      let index = 1;
      let sibling = current;
      while ((sibling = sibling.previousElementSibling) != null) {
        if (sibling.tagName === current.tagName) index += 1;
      }
      part += ':nth-of-type(' + index + ')';
      parts.unshift(part);
      current = current.parentElement;
    }
    return parts.join(' > ');
  }

  function clickNode(node) {
    if (!node) return false;
    node.dispatchEvent(new MouseEvent('mouseenter', { bubbles: true }));
    node.dispatchEvent(new MouseEvent('mouseover', { bubbles: true }));
    node.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }));
    node.dispatchEvent(new MouseEvent('mouseup', { bubbles: true }));
    node.click();
    return true;
  }

  function findCustomerServiceEntry(rootDocument, trail) {
    const selectors = [
      'a.action-link.customer-service',
      'a.action-item[data-click="BAR_咨询商家"]',
      'a.action-item[data-click*="咨询"]',
      'a[data-click*="咨询客服"]',
      'a[data-click*="咨询商家"]'
    ];

    for (const selector of selectors) {
      const matched = Array.from(rootDocument.querySelectorAll(selector)).find(node => visible(node));
      if (matched) {
        const text = (matched.innerText || matched.textContent || '').trim();
        return {
          target: matched,
          locator: trail + ' :: ' + cssPath(matched),
          matchedText: text || '客服'
        };
      }
    }

    const candidates = Array.from(rootDocument.querySelectorAll('a, button, [role="button"], div, span')).find(node => {
      if (!visible(node)) return false;
      const text = (node.innerText || node.textContent || '').replace(/\s+/g, ' ').trim();
      return text === '客服' || text === '咨询商家' || text === '联系商家';
    });
    if (candidates) {
      const text = (candidates.innerText || candidates.textContent || '').replace(/\s+/g, ' ').trim();
      return {
        target: candidates,
        locator: trail + ' :: ' + cssPath(candidates),
        matchedText: text
      };
    }

    const frames = Array.from(rootDocument.querySelectorAll('iframe'));
    for (let index = 0; index < frames.length; index += 1) {
      const frame = frames[index];
      try {
        const nested = frame.contentDocument
          ? findCustomerServiceEntry(frame.contentDocument, trail + ' > iframe[' + index + ']')
          : null;
        if (nested) return nested;
      } catch (error) {
        continue;
      }
    }
    return null;
  }

  const entry = findCustomerServiceEntry(document, 'document');
  if (!entry) {
    return JSON.stringify({
      ok: false,
      failureCode: 'CUSTOMER_SERVICE_ENTRY_NOT_FOUND',
      failureMessage: '当前商品页还没有找到可点击的客服入口。'
    });
  }

  clickNode(entry.target);
  return JSON.stringify({
    ok: true,
    locator: entry.locator,
    matchedText: entry.matchedText
  });
})();
