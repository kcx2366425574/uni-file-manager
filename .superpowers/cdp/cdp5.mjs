const ws = new WebSocket(process.argv[2])
let id = 1
const send = (m, p) => new Promise((res) => {
  const myId = id++
  const onMsg = (e) => { const x = JSON.parse(e.data); if (x.id === myId) { ws.removeEventListener('message', onMsg); res(x) } }
  ws.addEventListener('message', onMsg)
  ws.send(JSON.stringify({ id: myId, method: m, params: p }))
})
ws.onopen = async () => {
  const r = await send('Runtime.evaluate', {
    expression: `(() => {
      const img = document.querySelector('.cover img');
      if (!img) return 'NO IMG ELEMENT, cover html=' + (document.querySelector('.cover')?.outerHTML||'').slice(0,300);
      const cs = getComputedStyle(img);
      return JSON.stringify({
        srcHead: (img.getAttribute('src')||'').slice(0,40),
        natW: img.naturalWidth, natH: img.naturalHeight, complete: img.complete,
        rect: img.getBoundingClientRect(),
        display: cs.display, opacity: cs.opacity, visibility: cs.visibility, width: cs.width, height: cs.height
      });
    })()`,
    returnByValue: true
  })
  console.log(r.result?.result?.value)
  ws.close()
}
