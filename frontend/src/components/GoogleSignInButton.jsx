import { useEffect, useRef, useState } from 'react';

const clientId = import.meta.env.VITE_GOOGLE_CLIENT_ID;
let googleScriptPromise;

function loadGoogleScript() {
  if (window.google?.accounts?.id) return Promise.resolve();
  if (!googleScriptPromise) {
    googleScriptPromise = new Promise((resolve, reject) => {
      const script = document.createElement('script');
      script.src = 'https://accounts.google.com/gsi/client';
      script.async = true;
      script.defer = true;
      script.onload = resolve;
      script.onerror = () => reject(new Error('Không tải được nút đăng nhập Google.'));
      document.head.appendChild(script);
    }).catch((error) => {
      googleScriptPromise = null;
      throw error;
    });
  }
  return googleScriptPromise;
}

export default function GoogleSignInButton({ onCredential, text = 'continue_with', disabled = false }) {
  const containerRef = useRef(null);
  const callbackRef = useRef(onCredential);
  const [error, setError] = useState('');
  callbackRef.current = onCredential;

  useEffect(() => {
    if (!clientId) {
      setError('Chưa cấu hình GOOGLE_CLIENT_ID.');
      return;
    }
    let active = true;
    loadGoogleScript().then(() => {
      if (!active || !containerRef.current) return;
      window.google.accounts.id.initialize({
        client_id: clientId,
        callback: ({ credential }) => {
          if (active && credential) callbackRef.current(credential);
        },
      });
      containerRef.current.replaceChildren();
      window.google.accounts.id.renderButton(containerRef.current, {
        type: 'standard', theme: 'outline', size: 'large',
        shape: 'pill', text, width: Math.min(containerRef.current.clientWidth || 400, 400),
      });
    }).catch((failure) => { if (active) setError(failure.message); });
    return () => { active = false; };
  }, [text]);

  return <div className={`google-signin ${disabled ? 'google-signin-disabled' : ''}`}>
    <div ref={containerRef} className="google-signin-button" />
    {error && <p className="google-signin-error" role="alert">{error}</p>}
  </div>;
}
