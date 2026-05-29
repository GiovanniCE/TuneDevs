// Registration screen for creating a local user account; it provides aPI registration request and navigation back to login on success.

import React, { useState } from 'react';
import './Login.css'; // Reuse existing login styles

export default function Register({ apiUrl, onSwitchToLogin }) {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [isLoading, setIsLoading] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    const normalizedUsername = username.trim();
    const normalizedPassword = password.trim();
    setIsLoading(true);

    try {
      const response = await fetch(`${apiUrl}/api/register`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username: normalizedUsername, password: normalizedPassword })
      });

      if (response.ok) {
        alert('Registration successful! Please log in.');
        onSwitchToLogin();
      } else {
        const data = await response.text();
        alert(`Registration failed: ${data}`);
      }
    } catch (error) {
      console.error('Error:', error);
      alert('Registration failed. Is the backend running?');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="login-wrapper">
      <main className="login-card">
        <h1 className="title">Create Account</h1>
        <p className="subtitle">Sign up to get started</p>

        <form onSubmit={handleSubmit}>
          <div className="field">
            <label htmlFor="username">Username</label>
            <input
              id="username"
              type="text"
              placeholder="Choose a username"
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
              placeholder="Choose a password"
              required
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
          </div>

          <button type="submit" className="btn" disabled={isLoading}>
            {isLoading ? 'Creating Account...' : 'Sign Up'}
          </button>
        </form>

        <p className="footer">Already have an account? <a href="#" onClick={(e) => { e.preventDefault(); onSwitchToLogin(); }}>Log in</a></p>
      </main>
    </div>
  );
}
