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
      const card = document.querySelector('.card');
      const cover = document.querySelector('.cover');
      const img = document.querySelector('.cover img');
      const R = e => e ? (() => { const b=e.getBoundingClientRect(); return {x:Math.round(b.x),y:Math.round(b.y),w:Math.round(b.width),h:Math.round(b.height)} })() : null;
      return JSON.stringify({
        card: R(card), cover: R(cover), img: R(img),
        cardOverflow: card && getComputedStyle(card).overflow,
        coverOverflow: cover && getComputedStyle(cover).overflow,
        imgOpacity: img && getComputedStyle(img).opacity,
        imgNat: img && [img.naturalWidth, img.naturalHeight],
        imgComplete: img && img.complete
      });
    })()`,
    returnByValue: true
  })
  console.log(r.result?.result?.value)
  ws.close()
}
