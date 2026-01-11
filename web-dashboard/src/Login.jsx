import { useState } from 'react'
import './Login.css'

// Cuentas hardcodeadas
const USERS = {
  'ANDRES': 'ANDRES',
  'ALDI': 'ALDILINDA',
  'MATI': 'MATI'
}

export default function Login({ onLogin }) {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [isLoading, setIsLoading] = useState(false)

  const handleSubmit = (e) => {
    e.preventDefault()
    setError('')
    setIsLoading(true)

    // Simular delay para la animación
    setTimeout(() => {
      const user = username.toUpperCase()
      if (USERS[user] && USERS[user] === password) {
        localStorage.setItem('frioseguro_user', user)
        onLogin(user)
      } else {
        setError('Usuario o contraseña incorrectos')
        setIsLoading(false)
      }
    }, 800)
  }

  return (
    <div className="login-container">
      {/* Partículas de fondo animadas */}
      <div className="particles">
        {[...Array(20)].map((_, i) => (
          <div key={i} className="particle" style={{
            '--delay': `${Math.random() * 5}s`,
            '--duration': `${15 + Math.random() * 10}s`,
            '--x-start': `${Math.random() * 100}%`,
            '--x-end': `${Math.random() * 100}%`,
            '--size': `${4 + Math.random() * 8}px`
          }} />
        ))}
      </div>

      {/* Ondas de fondo */}
      <div className="waves">
        <div className="wave wave1"></div>
        <div className="wave wave2"></div>
        <div className="wave wave3"></div>
      </div>

      {/* Card de login */}
      <div className="login-card">
        <div className="login-header">
          <div className="logo-container">
            <div className="logo-icon">❄️</div>
            <div className="logo-glow"></div>
          </div>
          <h1 className="login-title">FrioSeguro</h1>
          <p className="login-subtitle">Sistema de Monitoreo de Reefers</p>
        </div>

        <form onSubmit={handleSubmit} className="login-form">
          <div className="input-group">
            <div className="input-icon">👤</div>
            <input
              type="text"
              placeholder="Usuario"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              className="login-input"
              autoComplete="username"
              required
            />
            <div className="input-line"></div>
          </div>

          <div className="input-group">
            <div className="input-icon">🔒</div>
            <input
              type="password"
              placeholder="Contraseña"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className="login-input"
              autoComplete="current-password"
              required
            />
            <div className="input-line"></div>
          </div>

          {error && (
            <div className="error-message">
              <span>⚠️</span> {error}
            </div>
          )}

          <button 
            type="submit" 
            className={`login-button ${isLoading ? 'loading' : ''}`}
            disabled={isLoading}
          >
            {isLoading ? (
              <div className="spinner"></div>
            ) : (
              <>
                <span>Ingresar</span>
                <div className="button-glow"></div>
              </>
            )}
          </button>
        </form>

        <div className="login-footer">
          <div className="company-badge">
            <span className="badge-text">PANDEMONIUM TECH</span>
            <span className="badge-separator">×</span>
            <span className="badge-text">PAN AMERICAN SILVER</span>
          </div>
        </div>
      </div>

      {/* Decoración de copos de nieve */}
      <div className="snowflakes">
        {[...Array(10)].map((_, i) => (
          <div key={i} className="snowflake" style={{
            '--delay': `${Math.random() * 8}s`,
            '--duration': `${10 + Math.random() * 15}s`,
            '--x': `${Math.random() * 100}vw`,
            '--size': `${0.8 + Math.random() * 0.5}em`
          }}>❄</div>
        ))}
      </div>
    </div>
  )
}
