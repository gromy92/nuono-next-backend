(() => {
  const supplierIdentity = decodeURIComponent(escape(window.atob('__SUPPLIER_BASE64__')));

  function visible(node) {
    if (!node) return false;
    const rect = node.getBoundingClientRect();
    return rect.width > 0 && rect.height > 0;
  }

  function normalize(text) {
    return (text || '')
      .toLowerCase()
      .replace(/[\s\u00a0]/g, '')
      .replace(/[()（）【】\[\]<>《》·,，.。:：;；!！?？'"`~@#$%^&*_+=|\\/.-]/g, '');
  }

  function buildIdentity(rawSupplier) {
    const normalized = normalize(rawSupplier);
    const stripped = normalized
      .replace(/有限责任公司/g, '')
      .replace(/有限公司/g, '')
      .replace(/股份有限公司/g, '')
      .replace(/公司/g, '')
      .replace(/旗舰店/g, '')
      .replace(/工厂店/g, '')
      .replace(/工厂/g, '')
      .replace(/厂/g, '')
      .replace(/商行/g, '')
      .replace(/贸易/g, '')
      .replace(/电子商务/g, '')
      .replace(/供应链/g, '')
      .replace(/实业/g, '')
      .replace(/科技/g, '')
      .replace(/制造/g, '');
    return {
      normalized,
      stripped
    };
  }

  function cssPath(node) {
    if (!node || !node.tagName) return 'unknown';
    const parts = [];
    let current = node;
    while (current && current.nodeType === 1 && parts.length < 7) {
      let part = current.tagName.toLowerCase();
      if (current.id) {
        part += '#' + current.id;
        parts.unshift(part);
        break;
      }
      const className = (current.className || '')
        .toString()
        .trim()
        .split(/\s+/)
        .filter(Boolean)
        .slice(0, 3)
        .join('.');
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
    if (!node) {
      return false;
    }
    node.dispatchEvent(new MouseEvent('mouseenter', { bubbles: true }));
    node.dispatchEvent(new MouseEvent('mouseover', { bubbles: true }));
    node.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }));
    node.dispatchEvent(new MouseEvent('mouseup', { bubbles: true }));
    node.click();
    return true;
  }

  function findClickableAncestor(node, maxDepth) {
    let current = node;
    let depth = 0;
    while (current && depth <= maxDepth) {
      if (current.tagName === 'BUTTON' || current.tagName === 'A' || current.getAttribute('role') === 'button') {
        return current;
      }
      if (typeof current.onclick === 'function') {
        return current;
      }
      current = current.parentElement;
      depth += 1;
    }
    return node;
  }

  function chooseBestMatch(rootDocument, trail, identity) {
    const nodes = Array.from(rootDocument.querySelectorAll('*')).filter(node => visible(node));
    const matches = [];
    nodes.forEach(node => {
      const rawText = (node.innerText || node.textContent || '').trim();
      if (!rawText || rawText.length > 80) {
        return;
      }
      const textCandidates = new Set();
      textCandidates.add(rawText);
      const lines = rawText.split(/\n+/).map(item => item.trim()).filter(Boolean);
      lines.forEach(line => {
        if (line.length <= 32) {
          textCandidates.add(line);
        }
      });
      if (lines.length >= 2) {
        textCandidates.add(lines.slice(0, 2).join(''));
      }

      textCandidates.forEach(candidateText => {
        const normalizedText = normalize(candidateText);
        if (normalizedText.length < 2) {
          return;
        }
        const isContainedBySupplier = identity.normalized.includes(normalizedText)
          || identity.stripped.includes(normalizedText);
        if (!isContainedBySupplier) {
          return;
        }
        const rect = node.getBoundingClientRect();
        let score = 1000;
        score += normalizedText.length * 12;
        if (normalizedText === identity.stripped || normalizedText === identity.normalized) score += 240;
        if (identity.stripped.endsWith(normalizedText)) score += 180;
        if (identity.normalized.endsWith(normalizedText)) score += 120;
        if (!rawText.includes('\n')) score += 80;
        if (rect.left < Math.max(window.innerWidth * 0.45, 420)) score += 80;
        if (rect.top > 0 && rect.top < Math.max(window.innerHeight - 120, 480)) score += 40;
        matches.push({
          node,
          text: candidateText,
          token: normalizedText,
          score,
          locator: trail + ' :: ' + cssPath(node)
        });
      });
    });

    if (!matches.length) {
      return null;
    }

    matches.sort((left, right) => right.score - left.score);
    return matches[0];
  }

  function ensureSelection(rootDocument, trail, identity) {
    const bodyText = ((rootDocument.body && rootDocument.body.innerText) || '').trim();
    const needSelection = bodyText.includes('您尚未选择联系人');
    const matched = chooseBestMatch(rootDocument, trail, identity);
    if (!matched) {
      return null;
    }
    if (!needSelection) {
      return {
        ok: true,
        selected: true,
        alreadySelected: true,
        matchedText: matched.text,
        matchedToken: matched.token,
        locator: matched.locator
      };
    }
    const target = findClickableAncestor(matched.node, 6);
    clickNode(target);
    return {
      ok: true,
      selected: true,
      alreadySelected: false,
      matchedText: matched.text,
      matchedToken: matched.token,
      locator: trail + ' :: ' + cssPath(target)
    };
  }

  const identity = buildIdentity(supplierIdentity);
  const currentDocumentResult = ensureSelection(document, 'document', identity);
  if (currentDocumentResult) {
    return JSON.stringify(currentDocumentResult);
  }
  const frames = Array.from(document.querySelectorAll('iframe'));
  for (let index = 0; index < frames.length; index += 1) {
    const frame = frames[index];
    try {
      if (frame.contentDocument) {
        const result = ensureSelection(frame.contentDocument, 'document > iframe[' + index + ']', identity);
        if (result) {
          return JSON.stringify(result);
        }
      }
    } catch (error) {
      continue;
    }
  }

  return JSON.stringify({
    ok: false,
    failureCode: 'SUPPLIER_THREAD_NOT_FOUND',
    failureMessage: '聊天页里还没有找到可自动点开的供应商联系人。'
  });
})();
