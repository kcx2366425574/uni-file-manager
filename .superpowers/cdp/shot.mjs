import fs from 'node:fs'
const ws = new WebSocket(process.argv[2])
let id = 1
const send = (m, p) => new Promise((res) => {
  const myId = id++
  const onMsg = (e) => { const x = JSON.parse(e.data); if (x.id === myId) { ws.removeEventListener('message', onMsg); res(x) } }
  ws.addEventListener('message', onMsg)
  ws.send(JSON.stringify({ id: myId, method: m, params: p }))
})
ws.onopen = async () => {
  const r = await send('Page.captureScreenshot', { format: 'png' })
  if (r.result?.data) {
    fs.writeFileSync(process.argv[3], Buffer.from(r.result.data, 'base64'))
    console.log('wrote', process.argv[3])
  } else {
    console.log('failed', JSON.stringify(r).slice(0, 300))
  }
  ws.close()
}
