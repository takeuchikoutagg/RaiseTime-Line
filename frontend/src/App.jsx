import { Navigate, Route, Routes } from 'react-router'
import { PublicOnly, RequireAuth } from './auth/RouteGuards.jsx'
import AppLayout from './components/AppLayout/AppLayout.jsx'
import LoginPage from './pages/LoginPage.jsx'
import PostDetailPage from './pages/PostDetailPage.jsx'
import SignupPage from './pages/SignupPage.jsx'
import TimelinePage from './pages/TimelinePage.jsx'

export default function App() {
  return (
    <Routes>
      <Route element={<PublicOnly />}>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/signup" element={<SignupPage />} />
      </Route>
      <Route element={<RequireAuth />}>
        {/* ログイン後の画面は、共通ヘッダーつきのレイアウトの中に表示する */}
        <Route element={<AppLayout />}>
          <Route path="/" element={<TimelinePage />} />
          <Route path="/posts/:postId" element={<PostDetailPage />} />
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
