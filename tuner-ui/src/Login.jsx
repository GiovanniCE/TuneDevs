// Login screen for registered users and guest access; it provides authentication callbacks that update App state.

import React, { useState } from 'react';
import './Login.css';

export default function Login({ apiUrl, onLoginSuccess, onSwitchToRegister, onEnterAsGuest }) {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [isLoading, setIsLoading] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    const normalizedUsername = username.trim();
    const normalizedPassword = password.trim();

    // Temporary local auth for development while backend/database is unavailable.
    if (normalizedUsername === 'user' && normalizedPassword === 'test') {
      onLoginSuccess(normalizedUsername);
      return;
    }

    setIsLoading(true);

    try {
      const response = await fetch(`${apiUrl}/api/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username: normalizedUsername, password: normalizedPassword })
      });

      if (response.ok) {
        onLoginSuccess(normalizedUsername);
      } else {
        const text = await response.text();
        alert(`Login failed: ${text || 'Invalid credentials'}`);
      }
    } catch (error) {
      console.error('Error:', error);
      alert('Login failed. Is the backend running?');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="login-wrapper">
      <main className="login-card">
        <h1 className="title">Welcome Back</h1>
        <p className="subtitle">Please sign in to continue</p>

        <form onSubmit={handleSubmit}>
          <div className="field">
            <label htmlFor="username">Username</label>
            <input
              id="username"
              type="text"
              placeholder="Enter your username"
              required
              value={username}
              onChange={(e) => setUsername(e.target.value)}
            />
          </div>

          <div className="field">
            <label htmlFor="password">Password</label>
            <input
              id="password"
              type="password"
              placeholder="Enter your password"
              required
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
          </div>

          <div className="row">
            <label>
              <input type="checkbox" style={{ width: 'auto', marginRight: '6px' }} />
              Remember me
            </label>
            <a href="#">Forgot password?</a>
          </div>

          <button type="submit" className="btn" disabled={isLoading}>
            {isLoading ? 'Logging in...' : 'Log In'}
          </button>

          <button type="button" className="btn btn-secondary" onClick={onEnterAsGuest}>
            {/* Guest mode skips auth and should not unlock user-only pages (Favorites). */}
            Enter as Guest
          </button>
        </form>

        <p className="footer">No account? <a href="#" onClick={(e) => { e.preventDefault(); onSwitchToRegister(); }}>Create one</a></p>
      </main>
    </div>
  );
}
