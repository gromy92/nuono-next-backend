(() => {
  const payload = decodeURIComponent(escape(window.atob('__PAYLOAD_BASE64__')));

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
      parts.unshift(part);
      current = current.parentElement;
    }
    return parts.join(' > ');
  }

  function readEditorText(target) {
    if (!target) return '';
    if (target instanceof HTMLInputElement || target instanceof HTMLTextAreaElement) {
      return (target.value || '').trim();
    }
    return (target.innerText || target.textContent || '').trim();
  }

  function findEditor(rootDocument, trail) {
    const editable = Array.from(rootDocument.querySelectorAll('[contenteditable=""], [contenteditable="true"], [role="textbox"]'))
      .find(node => visible(node));
    if (editable) {
      return { target: editable, locator: trail + ' :: ' + cssPath(editable) };
    }
    const inputs = Array.from(rootDocument.querySelectorAll('textarea, input')).find(node => {
      if (!visible(node) || node.disabled || node.readOnly) return false;
      const placeholder = (node.getAttribute('placeholder') || '').trim();
      return !/(搜索|search)/i.test(placeholder);
    });
    if (inputs) {
      return { target: inputs, locator: trail + ' :: ' + cssPath(inputs) };
    }
    const frames = Array.from(rootDocument.querySelectorAll('iframe'));
    for (let index = 0; index < frames.length; index += 1) {
      const frame = frames[index];
      try {
        const nested = frame.contentDocument ? findEditor(frame.contentDocument, trail + ' > iframe[' + index + ']') : null;
        if (nested) return nested;
      } catch (error) {
        continue;
      }
    }
    return null;
  }

  function findSendButton(rootDocument, trail) {
    const buttons = Array.from(rootDocument.querySelectorAll('button, [role="button"], a, span, div'));
    const matched = buttons.find(node => {
      if (!visible(node)) return false;
      const text = (node.innerText || node.textContent || '').trim();
      return /^发送$/.test(text) || /(send message|发送消息)/i.test(text);
    });
    if (matched) {
      return { target: matched, locator: trail + ' :: ' + cssPath(matched) };
    }
    const frames = Array.from(rootDocument.querySelectorAll('iframe'));
    for (let index = 0; index < frames.length; index += 1) {
      const frame = frames[index];
      try {
        const nested = frame.contentDocument ? findSendButton(frame.contentDocument, trail + ' > iframe[' + index + ']') : null;
        if (nested) return nested;
      } catch (error) {
        continue;
      }
    }
    return null;
  }

  const editor = findEditor(document, 'document');
  if (!editor) {
    return JSON.stringify({
      ok: false,
      failureCode: 'INPUT_NOT_FOUND',
      failureMessage: '真实聊天页没有找到可写输入区。'
    });
  }

  const beforeText = readEditorText(editor.target);
  if (!beforeText || !beforeText.includes(payload.slice(0, Math.min(payload.length, 12)))) {
    return JSON.stringify({
      ok: false,
      failureCode: 'INPUT_MISMATCH',
      failureMessage: '发送前输入区里不是本次询价内容。',
      beforeText
    });
  }

  const sendButton = findSendButton(document, 'document');
  if (sendButton) {
    sendButton.target.click();
    return JSON.stringify({
      ok: true,
      triggerType: 'button',
      locator: editor.locator,
      sendControlLocator: sendButton.locator,
      beforeText
    });
  }

  editor.target.focus();
  ['keydown', 'keypress', 'keyup'].forEach(type => {
    editor.target.dispatchEvent(new KeyboardEvent(type, {
      key: 'Enter',
      code: 'Enter',
      keyCode: 13,
      which: 13,
      bubbles: true
    }));
  });

  return JSON.stringify({
    ok: true,
    triggerType: 'enter',
    locator: editor.locator,
    beforeText
  });
})();
