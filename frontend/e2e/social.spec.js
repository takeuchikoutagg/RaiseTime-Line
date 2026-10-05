import { expect, test } from '@playwright/test'
import { apiAs, createComment, createPost, follow, newUser, signup } from './support.js'

// テスト仕様書 SC-03 フォローとタイムライン・SC-04 いいね・コメント・SC-06 ユーザー検索（F-03・F-05〜F-09）
test('検索 → フォロー → タイムラインに出る → いいね・コメント → フォロー解除', async ({ page }) => {
  const bob = newUser('E2Eボブ')
  const bobApi = await apiAs(bob)
  await createPost(bobApi, 'ボブの投稿です')
  await bobApi.dispose()
  await signup(page.request, newUser('E2Eアリス'))
  await page.goto('/')

  // SC-06-2・SC-03-1 先頭の @ と大文字・小文字を無視して検索できる
  await page.getByRole('link', { name: '検索' }).click()
  await page.getByRole('searchbox', { name: 'ユーザー検索' }).fill(`@${bob.username.toUpperCase()}`)
  const results = page.getByRole('list', { name: '検索結果' })
  await expect(results.getByRole('listitem')).toHaveCount(1)

  // SC-03-2・3 ユーザーページでフォローすると「フォロー中」、フォロワー数+1
  await results.getByRole('link', { name: new RegExp(`E2Eボブ.*@${bob.username}`) }).click()
  await expect(page).toHaveURL(new RegExp(`/users/${bob.username}$`))
  const profile = page.getByRole('region', { name: 'プロフィール' })
  await expect(profile).toContainText('0 フォロワー')
  await profile.getByRole('button', { name: 'フォローする' }).click()
  const following = profile.getByRole('button', { name: /^フォロー中/ })
  await expect(following).toBeVisible()
  await expect(profile).toContainText('1 フォロワー')

  // SC-03-4 ホームのタイムラインにボブの投稿が出る
  await page.getByRole('link', { name: 'ホーム' }).click()
  const post = page.getByRole('article').filter({ hasText: 'ボブの投稿です' })
  await expect(post).toBeVisible()

  // SC-04-1・2 いいね（再読み込みしても残る）→ 取り消し
  await post.getByRole('button', { name: 'いいね 0件' }).click()
  await expect(post.getByRole('button', { name: 'いいね 1件' })).toBeVisible()
  await page.reload()
  await expect(post.getByRole('button', { name: 'いいね 1件' })).toBeVisible()
  await post.getByRole('button', { name: 'いいね 1件' }).click()
  await expect(post.getByRole('button', { name: 'いいね 0件' })).toBeVisible()

  // SC-04-3〜6 投稿詳細でコメントする。空白だけは送れない。HTML は文字のまま出る
  await post.getByRole('link', { name: 'コメント 0件' }).click()
  await expect(page.getByText(/^\d{4}\/\d{2}\/\d{2} \d{2}:\d{2}$/)).toBeVisible()
  const commentBox = page.getByRole('textbox', { name: 'コメント' })
  const send = page.getByRole('button', { name: '送信' })
  await commentBox.fill('  　')
  await expect(send).toBeDisabled()
  const html = '<b>太字</b><img src=x onerror=alert(1)>'
  await commentBox.fill(`E2E のコメント ${html}`)
  await send.click()
  const comments = page.getByRole('region', { name: 'コメント一覧' })
  await expect(comments.getByText(`E2E のコメント ${html}`)).toBeVisible()
  await expect(comments.locator('b, img[src="x"]')).toHaveCount(0)
  // 投稿詳細の投稿本体のコメント数も+1（詳細ではリンクではなく文字）
  await expect(page.getByLabel('コメント 1件')).toBeVisible()
  await expect(commentBox).toHaveValue('')

  // SC-03-6・7 マウスを乗せると「フォロー解除」。押すと解除し、タイムラインに出なくなる
  await page.goto(`/users/${bob.username}`)
  await following.hover()
  await expect(following.getByText('フォロー解除')).toBeVisible()
  await following.click()
  await expect(profile.getByRole('button', { name: 'フォローする' })).toBeVisible()
  await expect(profile).toContainText('0 フォロワー')
  await page.getByRole('link', { name: 'ホーム' }).click()
  await expect(page.getByRole('link', { name: 'ユーザーを探す' })).toBeVisible()
  await expect(page.getByText('ボブの投稿です')).toBeHidden()
})

// SC-06-1・3・4 検索の特殊文字・該当なし・結果からフォロー
test('「_」は @ID に _ を含む人だけ。該当なし。検索結果からフォローできる', async ({ page }) => {
  const target = newUser('E2E検索対象')
  await (await apiAs(target)).dispose()
  await signup(page.request, newUser())
  await page.goto('/search')
  const box = page.getByRole('searchbox', { name: 'ユーザー検索' })
  const results = page.getByRole('list', { name: '検索結果' })

  await box.fill('_')
  await expect(results.getByRole('listitem').first()).toBeVisible()
  for (const text of await results.getByRole('listitem').allInnerTexts()) {
    expect(text).toMatch(/@\S*_/)
  }

  await box.fill('zzz_not_found')
  await expect(page.getByText('該当するユーザーが見つかりません')).toBeVisible()

  await box.fill(target.username)
  const row = results.getByRole('listitem').filter({ hasText: `@${target.username}` })
  await row.getByRole('button', { name: 'フォローする' }).click()
  await expect(row.getByRole('button', { name: /^フォロー中/ })).toBeVisible()
})

// SC-03-5・SC-04-7 タイムライン・コメント一覧の無限スクロール（20件ずつ）
test('タイムラインとコメント一覧は、下までスクロールすると続きを読み込む', async ({ page }) => {
  const bob = newUser('E2E多投稿')
  const bobApi = await apiAs(bob)
  let last
  for (let i = 1; i <= 25; i++) {
    last = await createPost(bobApi, `たくさんの投稿 ${i}`)
  }
  for (let i = 1; i <= 25; i++) {
    await createComment(bobApi, last.id, `たくさんのコメント ${i}`)
  }
  await bobApi.dispose()
  await signup(page.request, newUser())
  await follow(page.request, bob.username)

  await page.goto('/')
  const posts = page.getByRole('article')
  await expect(posts).toHaveCount(20)
  await expect(page.getByText('これ以上の投稿はありません')).toBeHidden()
  await posts.last().scrollIntoViewIfNeeded()
  await expect(posts).toHaveCount(25)
  await expect(page.getByText('これ以上の投稿はありません')).toBeVisible()

  await page.goto(`/posts/${last.id}`)
  const comments = page.getByRole('region', { name: 'コメント一覧' }).getByRole('article')
  await expect(comments).toHaveCount(20)
  await expect(comments.first()).toContainText('たくさんのコメント 1')
  await comments.last().scrollIntoViewIfNeeded()
  await expect(comments).toHaveCount(25)
  await expect(comments.last()).toContainText('たくさんのコメント 25')
})
