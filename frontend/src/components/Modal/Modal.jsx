import { useEffect, useId, useRef } from 'react'
import styles from './Modal.module.css'

// Taskmanagement（AI-java-study）の Modal をもとにしたもの。
// 開いたらモーダルの中にフォーカスを移し、Tab はモーダルの中だけを回る。Esc・背景のクリック・×で閉じる

const FOCUSABLE_SELECTOR = 'button:not(:disabled), [href], input:not(:disabled), select, textarea, [tabindex]:not([tabindex="-1"])'

export default function Modal({ title, onClose, children, variant = 'dialog' }) {
  const titleId = useId()
  const dialogRef = useRef(null)

  // マウント時にモーダルの中の最初の操作できる要素へフォーカスを移し、閉じたら元の場所に戻す
  useEffect(() => {
    const previouslyFocused = document.activeElement
    const focusable = dialogRef.current?.querySelectorAll(FOCUSABLE_SELECTOR)
    // 先頭は「閉じる」ボタンなので、入力欄などがあればそちらを優先する
    const first = [...(focusable ?? [])].find((el) => !el.dataset.closeButton) ?? focusable?.[0]
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

      const focusable = dialogRef.current?.querySelectorAll(FOCUSABLE_SELECTOR)
      if (!focusable || focusable.length === 0) return
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
