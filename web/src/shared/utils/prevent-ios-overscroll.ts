function isIosDevice(): boolean {
  return /iPad|iPhone|iPod/.test(navigator.userAgent) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);
}

export function preventIosOverscroll(): () => void {
  if (!isIosDevice()) {
    return () => {};
  }

  let lastX = 0;
  let lastY = 0;

  const onTouchStart = (event: TouchEvent) => {
    const touch = event.touches.item(0);
    if (event.touches.length === 1 && touch) {
      lastX = touch.clientX;
      lastY = touch.clientY;
    }
  };

  const onTouchMove = (event: TouchEvent) => {
    const touch = event.touches.item(0);
    if (!event.cancelable || event.touches.length !== 1 || !touch) {
      return;
    }

    const x = touch.clientX;
    const y = touch.clientY;
    const deltaX = x - lastX;
    const deltaY = y - lastY;
    lastX = x;
    lastY = y;

    if (Math.abs(deltaY) <= Math.abs(deltaX)) {
      return;
    }

    const page = document.scrollingElement;
    if (!page) {
      return;
    }

    const atPageEdge = deltaY > 0 ? page.scrollTop <= 0 : page.scrollTop + page.clientHeight >= page.scrollHeight - 1;
    if (!atPageEdge) {
      return;
    }

    let element = event.target instanceof Element ? event.target : null;
    while (element && element !== page) {
      if (element instanceof HTMLElement) {
        const overflowY = window.getComputedStyle(element).overflowY;
        const canScroll =
          (overflowY === 'auto' || overflowY === 'scroll') &&
          element.scrollHeight > element.clientHeight + 1 &&
          (deltaY > 0 ? element.scrollTop > 0 : element.scrollTop + element.clientHeight < element.scrollHeight - 1);

        if (canScroll) {
          return;
        }
      }

      element = element.parentElement;
    }

    event.preventDefault();
  };

  document.addEventListener('touchstart', onTouchStart, { passive: true });
  document.addEventListener('touchmove', onTouchMove, { passive: false });

  return () => {
    document.removeEventListener('touchstart', onTouchStart);
    document.removeEventListener('touchmove', onTouchMove);
  };
}
