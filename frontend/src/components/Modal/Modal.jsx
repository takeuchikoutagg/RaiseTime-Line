import { useEffect, useId, useRef } from 'react'
import styles from './Modal.module.css'

// Taskmanagement（AI-java-study）の Modal をもとにしたもの。
// 開いたらモーダルの中にフォーカスを移し、Tab はモーダルの中だけを回る。Esc・背景のクリック・×で閉じる

const FOCUSABLE_SELECTOR = 'button:not(:disabled), [href], input:not(:disabled), select, textarea, [tabindex]:not([tabindex="-1"])'
// 文字を入力する欄（開いたときに、ボタンより優先してフォーカスを移す）
const TEXT_FIELD_SELECTOR = 'input:not([type="file"]):not([type="checkbox"]):not([type="radio"]), textarea, select'

// モーダルの中の、フォーカスできる部品。画面に出していない部品（hidden のファイル選択欄など）は
// フォーカスできないので除く（含めると、そこへ移そうとして失敗し、フォーカスがモーダルの外に残る）
function focusableElements(dialog) {
  return [...(dialog?.querySelectorAll(FOCUSABLE_SELECTOR) ?? [])].filter((el) => !el.closest('[hidden]'))
}

export default function Modal({ title, onClose, children, variant = 'dialog' }) {
  const titleId = useId()
  const dialogRef = useRef(null)

  // マウント時にモーダルの中の最初の操作できる要素へフォーカスを移し、閉じたら元の場所に戻す
  useEffect(() => {
    const previouslyFocused = document.activeElement
    const focusable = focusableElements(dialogRef.current)
    // 先頭は「閉じる」ボタンなので、文字を入力する欄 → そのほかの部品 → 「閉じる」の順に探す
    const first =
      focusable.find((el) => el.matches(TEXT_FIELD_SELECTOR)) ??
      focusable.find((el) => !el.dataset.closeButton) ??
      focusable[0]
    if (first) {
      first.focus()
    } else {
      dialogRef.current?.focus()
    }
    return () => {
      previouslyFocused?.focus?.()
    }
  }, [])

  useEffect(() => {
    const handleKeyDown = (event) => {
      if (event.key === 'Escape') {
        onClose()
        return
      }
      if (event.key !== 'Tab') return

      const focusable = focusableElements(dialogRef.current)
      if (focusable.length === 0) return
      const first = focusable[0]
      const last = focusable[focusable.length - 1]
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault()
        last.focus()
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault()
        first.focus()
      }
    }
    document.addEventListener('keydown', handleKeyDown)
    return () => document.removeEventListener('keydown', handleKeyDown)
  }, [onClose])

  return (
    // 背景のクリックで閉じる操作は、Esc キーでも同じことができるため
    // oxlint-disable-next-line jsx_a11y/click-events-have-key-events, jsx_a11y/no-static-element-interactions
    <div className={styles.overlay} onClick={onClose}>
      {/* モーダルの中のクリックが背景に伝わらないよう止めるだけ（role は付けてある） */}
      {/* oxlint-disable-next-line jsx_a11y/click-events-have-key-events, jsx_a11y/no-static-element-interactions */}
      <div
        ref={dialogRef}
        className={styles.dialog}
        role={variant === 'alert' ? 'alertdialog' : 'dialog'}
        aria-modal="true"
        aria-labelledby={titleId}
        tabIndex={-1}
        onClick={(event) => event.stopPropagation()}
      >
        <div className={styles.header}>
          <h2 id={titleId} className={styles.title}>
            {title}
          </h2>
          <button type="button" className={styles.closeButton} onClick={onClose} aria-label="閉じる" data-close-button>
            ×
          </button>
        </div>
        <div className={styles.body}>{children}</div>
      </div>
    </div>
  )
}
