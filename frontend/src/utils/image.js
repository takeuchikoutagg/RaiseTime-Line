// 画像を送る前に、ブラウザの中で縮める（prototype の resizeImage と同じ考え方）。
// スマホで撮った写真（数MB・数千px）をそのまま送ると、保存先の容量も、表示するときの通信量も大きくなるため。
// - 投稿の画像：長い辺を 1600px まで（タイムラインでは横幅 600px 程度で表示する）
// - プロフィール画像：真ん中を正方形に切り抜いて 400×400px（アイコンは 36〜72px で表示する）
// どちらも JPEG（画質 0.85）にする。透過 PNG の透明な部分は白にする（JPEG は透過できないため）。
// 作り直した画像には、撮影場所（GPS）などの EXIF は入らない

export const POST_IMAGE_MAX_SIDE = 1600
export const AVATAR_SIZE = 400
const JPEG_QUALITY = 0.85

// 縮める必要がない小さな画像（この大きさ以下で、長い辺も上限以下）は、そのまま送る（PNG の透過などを残すため）
const KEEP_ORIGINAL_BYTES = 1024 * 1024

// 長い辺が maxSide に収まる大きさ（大きくはしない）
export function fitWithin(width, height, maxSide) {
  const scale = Math.min(1, maxSide / Math.max(width, height))
  return { width: Math.round(width * scale), height: Math.round(height * scale) }
}

// 真ん中の正方形を切り抜く位置と大きさ
export function centerSquare(width, height) {
  const side = Math.min(width, height)
  return { sx: Math.round((width - side) / 2), sy: Math.round((height - side) / 2), side }
}

// 画像として読み込めないファイル（拡張子だけ画像のファイルなど）のときに投げる
export class ImageLoadError extends Error {}

function loadImage(file) {
  return new Promise((resolve, reject) => {
    const url = URL.createObjectURL(file)
    const img = new Image()
    img.onload = () => {
      URL.revokeObjectURL(url)
      resolve(img)
    }
    img.onerror = () => {
      URL.revokeObjectURL(url)
      reject(new ImageLoadError('画像を読み込めませんでした'))
    }
    img.src = url
  })
}

function toJpegFile(canvas, originalName) {
  return new Promise((resolve, reject) => {
    canvas.toBlob(
      (blob) => {
        if (!blob) {
          reject(new ImageLoadError('画像を変換できませんでした'))
          return
        }
        const name = originalName.replace(/\.[^.]*$/, '') + '.jpg'
        resolve(new File([blob], name, { type: 'image/jpeg' }))
      },
      'image/jpeg',
      JPEG_QUALITY,
    )
  })
}

function drawOnWhite(width, height, draw) {
  const canvas = document.createElement('canvas')
  canvas.width = width
  canvas.height = height
  const ctx = canvas.getContext('2d')
  ctx.fillStyle = '#ffffff'
  ctx.fillRect(0, 0, width, height)
  draw(ctx)
  return canvas
}

// JPEG・WebP は、撮影場所（GPS）などの EXIF が入っていることがある。作り直すと消えるので、小さくても必ず作り直す
// （サーバーでも保存する前に取り除く）。PNG・GIF に位置情報が入ることはほぼない
export function mayContainLocation(file) {
  return file.type === 'image/jpeg' || file.type === 'image/webp'
}

// 投稿の画像。アニメーション GIF は縮めると動かなくなるので、そのまま送る
export async function resizeForPost(file) {
  if (file.type === 'image/gif') return file
  const img = await loadImage(file)
  const { width, height } = fitWithin(img.naturalWidth, img.naturalHeight, POST_IMAGE_MAX_SIDE)
  const locationRisk = mayContainLocation(file)
  if (!locationRisk && width === img.naturalWidth && file.size <= KEEP_ORIGINAL_BYTES) return file
  const canvas = drawOnWhite(width, height, (ctx) => ctx.drawImage(img, 0, 0, width, height))
  const resized = await toJpegFile(canvas, file.name)
  if (locationRisk) return resized
  // 図やスクリーンショット（PNG）のような画像は、JPEG にするとかえって大きくなることがある。そのときは元のファイルを送る
  return smallerOf(file, resized)
}

// 送る量（バイト数）が少ないほうを選ぶ
export function smallerOf(original, resized) {
  return resized.size < original.size ? resized : original
}

// プロフィール画像。真ん中を正方形に切り抜き、400×400px（元が小さければその大きさ）にする
export async function resizeForAvatar(file) {
  const img = await loadImage(file)
  const { sx, sy, side } = centerSquare(img.naturalWidth, img.naturalHeight)
  const size = Math.min(side, AVATAR_SIZE)
  const canvas = drawOnWhite(size, size, (ctx) => ctx.drawImage(img, sx, sy, side, side, 0, 0, size, size))
  return toJpegFile(canvas, file.name)
}
