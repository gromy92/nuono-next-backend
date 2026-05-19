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

  function readEditorText(target) {
    if (!target) return '';
    if (target instanceof HTMLInputElement || target instanceof HTMLTextAreaElement) {
      return (target.value || '').trim();
    }
    return (target.innerText || target.textContent || '').trim();
  }

  function setEditorText(target, text) {
    if (target instanceof HTMLInputElement || target instanceof HTMLTextAreaElement) {
      target.focus();
      target.value = text;
      target.dispatchEvent(new Event('input', { bubbles: true }));
      target.dispatchEvent(new Event('change', { bubbles: true }));
      return;
    }
    target.focus();
    target.innerText = text;
    target.dispatchEvent(new InputEvent('input', { bubbles: true, data: text, inputType: 'insertText' }));
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

  const editor = findEditor(document, 'document');
  if (!editor) {
    return JSON.stringify({
      ok: false,
      failureCode: 'INPUT_NOT_FOUND',
      failureMessage: '真实聊天页没有找到可写输入区。'
    });
  }

  setEditorText(editor.target, payload);
  const editorText = readEditorText(editor.target);
  return JSON.stringify({
    ok: editorText.includes(payload.slice(0, Math.min(payload.length, 12))),
    locator: editor.locator,
    editorText,
    bodyText: ((document.body && document.body.innerText) || '').slice(0, 4000),
    evidence: '已把询价内容写入真实聊天输入区。'
  });
})();
