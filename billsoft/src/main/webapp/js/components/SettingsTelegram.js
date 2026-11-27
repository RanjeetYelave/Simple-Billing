/**
 * RupeeCRM - Telegram Remote Assistant Settings Workspace Component
 * 
 * Provides desktop-controlled bot token configuration, secure OS credential storage,
 * pairing challenge generation, and authorized account management with instant revocation.
 */

(function () {
  'use strict';

  const { useState, useEffect, useRef } = React;

  function SettingsTelegram() {
    const isDesktop = typeof window !== 'undefined' && window.rupeeDesktop && window.rupeeDesktop.telegram;

    const [status, setStatus] = useState(null);
    const [loading, setLoading] = useState(true);
    const [fetchError, setFetchError] = useState(null);
    const [tokenInput, setTokenInput] = useState('');
    const [submittingToken, setSubmittingToken] = useState(false);
    const [tokenError, setTokenError] = useState(null);

    const [users, setUsers] = useState([]);
    const [challengeCode, setChallengeCode] = useState(null);
    const [challengeExpiry, setChallengeExpiry] = useState(null);
    const [generatingChallenge, setGeneratingChallenge] = useState(false);
    const [targetUserIdInput, setTargetUserIdInput] = useState('');

    const [testChatIdInput, setTestChatIdInput] = useState('');
    const [testSending, setTestSending] = useState(false);
    const [testResult, setTestResult] = useState(null);
    const [copiedChallenge, setCopiedChallenge] = useState(false);

    const mountedRef = useRef(true);

    // Refresh status and users
    const refreshData = async () => {
      if (!isDesktop) {
        if (mountedRef.current) setLoading(false);
        return;
      }
      try {
        const [statRes, userRes] = await Promise.all([
          window.rupeeDesktop.telegram.getStatus(),
          window.rupeeDesktop.telegram.getUsers()
        ]);
        if (!mountedRef.current) return;
        if (statRes && statRes.success) {
          setStatus(statRes);
          setFetchError(null);
        } else if (statRes && !statRes.success) {
          setFetchError(statRes.error || 'Failed to communicate with Telegram bridge');
        }
        if (userRes && userRes.success) {
          setUsers(userRes.users || []);
        }
      } catch (err) {
        if (mountedRef.current) {
          setFetchError(err.message || 'Failed connecting to desktop assistant backend');
        }
      } finally {
        if (mountedRef.current) {
          setLoading(false);
        }
      }
    };

    useEffect(() => {
      mountedRef.current = true;
      refreshData();
      const interval = setInterval(() => {
        if (mountedRef.current) refreshData();
      }, 10000);
      return () => {
        mountedRef.current = false;
        clearInterval(interval);
      };
    }, [isDesktop]);

    // Challenge countdown timer
    useEffect(() => {
      if (!challengeExpiry) return;
      const t = setInterval(() => {
        const left = challengeExpiry - Date.now();
        if (left <= 0) {
          if (mountedRef.current) {
            setChallengeCode(null);
            setChallengeExpiry(null);
          }
        }
      }, 1000);
      return () => clearInterval(t);
    }, [challengeExpiry]);

    const handleConnectBot = async (e) => {
      e.preventDefault();
      if (!tokenInput || !tokenInput.trim()) return;
      setSubmittingToken(true);
      setTokenError(null);

      try {
        const res = await window.rupeeDesktop.telegram.configure({ token: tokenInput.trim() });
        if (res && res.success) {
          setTokenInput('');
          await refreshData();
        } else {
          setTokenError(res && res.error ? res.error : 'Failed to connect bot. Verify token.');
        }
      } catch (err) {
        setTokenError(err.message || 'Error configuring bot');
      } finally {
        setSubmittingToken(false);
      }
    };

    const handleToggleEnabled = async (enabled) => {
      try {
        await window.rupeeDesktop.telegram.toggle({ enabled });
        await refreshData();
      } catch (err) {
        console.error('Toggle error:', err);
      }
    };

    const handleGenerateChallenge = async () => {
      setGeneratingChallenge(true);
      try {
        const payload = {};
        if (targetUserIdInput && targetUserIdInput.trim()) {
          payload.targetUserId = targetUserIdInput.trim();
        }
        const res = await window.rupeeDesktop.telegram.generateChallenge(payload);
        if (res && res.success && res.challengeCode) {
          setChallengeCode(res.challengeCode);
          setChallengeExpiry(Date.now() + ((res.expiresInSeconds || 600) * 1000));
        } else if (res && res.error) {
          alert('Failed generating challenge: ' + res.error);
        }
      } catch (err) {
        alert('Failed generating challenge: ' + err.message);
      } finally {
        setGeneratingChallenge(false);
      }
    };

    const handleApproveUser = async (userId) => {
      try {
        const res = await window.rupeeDesktop.telegram.approveUser({ userId });
        if (res && res.success) {
          await refreshData();
        } else {
          alert('Approval failed: ' + (res && res.error ? res.error : 'Unknown error'));
        }
      } catch (err) {
        alert('Approval failed: ' + err.message);
      }
    };

    const handleSendTestMessage = async () => {
      if (!testChatIdInput || !testChatIdInput.trim()) {
        alert('Please enter a target Chat ID or Telegram User ID to send a diagnostic message.');
        return;
      }
      setTestSending(true);
      setTestResult(null);
      try {
        const res = await window.rupeeDesktop.telegram.sendTestMessage({
          chatId: testChatIdInput.trim(),
          text: '🧪 *RupeeCRM Diagnostic Test Message*\nBot communication is fully operational!'
        });
        if (res && res.success) {
          setTestResult({ success: true, message: 'Test message sent successfully (Msg ID: ' + res.messageId + ')' });
        } else {
          setTestResult({ success: false, message: res && res.error ? res.error : 'Failed to send test message' });
        }
      } catch (err) {
        setTestResult({ success: false, message: err.message });
      } finally {
        setTestSending(false);
      }
    };

    const handleRevokeUser = async (userId) => {
      if (!confirm('Are you sure you want to revoke this Telegram account? It will immediately lose all access to RupeeCRM across all firms.')) {
        return;
      }
      try {
        await window.rupeeDesktop.telegram.revokeUser({ userId });
        await refreshData();
      } catch (err) {
        alert('Revocation failed: ' + err.message);
      }
    };

    const handleResumePoller = async () => {
      try {
        await window.rupeeDesktop.telegram.resumePoller();
        await refreshData();
      } catch (err) {
        alert('Resume failed: ' + err.message);
      }
    };

    const handleDisconnectBot = async () => {
      if (!confirm('Disconnect this bot? Credentials will be erased from local OS secure storage and polling will stop.')) {
        return;
      }
      try {
        await window.rupeeDesktop.telegram.disconnect();
        await refreshData();
      } catch (err) {
        alert('Disconnect failed: ' + err.message);
      }
    };

    if (!isDesktop) {
      return React.createElement('div', { className: 'rcrm-card', style: { padding: 24, textAlign: 'center' } },
        React.createElement('div', { style: { fontSize: '2rem', marginBottom: 12 } }, '📱'),
        React.createElement('h3', { style: { margin: '0 0 8px 0', fontWeight: 700 } }, 'Telegram Assistant'),
        React.createElement('p', { style: { color: 'var(--text-muted, #64748b)', maxWidth: 500, margin: '0 auto' } },
          'The Telegram Remote Assistant uses local OS Keychain / DPAPI hardware-level credential protection and operates exclusively inside the RupeeCRM desktop application.'
        )
      );
    }

    if (loading && !status && !fetchError) {
      return React.createElement('div', { style: { padding: 40, textAlign: 'center', color: 'var(--text-muted)' } },
        React.createElement('div', { style: { fontSize: '1.8rem', marginBottom: 12 } }, '⏳'),
        React.createElement('div', { style: { fontWeight: 600, fontSize: '1rem', marginBottom: 6 } }, 'Connecting to Telegram Assistant...'),
        React.createElement('p', { style: { margin: 0, fontSize: '0.85rem' } }, 'Querying local poller state and secure credential store...')
      );
    }

    if (fetchError && !status) {
      return React.createElement('div', { className: 'rcrm-card', style: { padding: 32, textAlign: 'center', background: '#fff', border: '1px solid #fee2e2', borderRadius: 12 } },
        React.createElement('div', { style: { fontSize: '2rem', marginBottom: 12 } }, '⚠️'),
        React.createElement('h3', { style: { margin: '0 0 8px 0', color: '#991b1b', fontWeight: 700 } }, 'Telegram Assistant Unavailable'),
        React.createElement('p', { style: { color: 'var(--text-muted, #64748b)', maxWidth: 480, margin: '0 auto 16px auto', fontSize: '0.9rem' } },
          fetchError
        ),
        React.createElement('button', {
          className: 'rcrm-btn',
          onClick: () => {
            setLoading(true);
            setFetchError(null);
            refreshData();
          },
          style: { padding: '8px 20px', background: '#059669', color: '#fff', border: 'none', borderRadius: 6, fontWeight: 600, cursor: 'pointer' }
        }, '🔄 Retry Connection')
      );
    }

    const isConfigured = status && status.botUsername && status.botUsername.length > 0;
    const isRunning = status && status.isRunning;
    const isTripped = status && status.circuitBreakerTripped;

    return React.createElement('div', { style: { display: 'flex', flexDirection: 'column', gap: 16, maxWidth: 1040 } },
      
      // Card 1: Bot Connection & Status
      React.createElement('div', { className: 'rcrm-card', style: { padding: '16px 20px', borderRadius: 8, border: '1px solid var(--border-color, #e2e8f0)', background: 'var(--card-bg, #fff)' } },
        React.createElement('div', { style: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 10, marginBottom: 12 } },
          React.createElement('div', null,
            React.createElement('h3', { style: { margin: 0, fontSize: '1.05rem', fontWeight: 700, display: 'flex', alignItems: 'center', gap: 8 } },
              'Telegram Business Assistant',
              isConfigured && React.createElement('span', {
                style: {
                  fontSize: '0.72rem',
                  fontWeight: 600,
                  padding: '2px 8px',
                  borderRadius: 999,
                  background: isTripped ? '#fee2e2' : (isRunning ? '#dcfce7' : '#fef3c7'),
                  color: isTripped ? '#991b1b' : (isRunning ? '#166534' : '#92400e')
                }
              }, isTripped ? '⚠️ Poller Suspended (Conflict)' : (isRunning ? '🟢 Active Polling' : '🟠 Stopped'))
            ),
            React.createElement('p', { style: { margin: '2px 0 0 0', fontSize: '0.8rem', color: 'var(--text-muted, #64748b)' } },
              'Operate your local RupeeCRM installation remotely from mobile Telegram with zero cloud hosting.'
            )
          ),
          isConfigured && React.createElement('div', { style: { display: 'flex', gap: 8 } },
            React.createElement('button', {
              className: 'rcrm-btn',
              style: { padding: '5px 12px', fontSize: '0.8rem', background: isRunning ? '#fef3c7' : '#dcfce7', color: isRunning ? '#92400e' : '#166534', border: '1px solid ' + (isRunning ? '#fde68a' : '#bbf7d0'), borderRadius: 6, cursor: 'pointer', fontWeight: 600 },
              onClick: () => handleToggleEnabled(!status.isEnabled)
            }, status.isEnabled ? 'Pause Assistant' : 'Resume Assistant'),
            React.createElement('button', {
              className: 'rcrm-btn',
              style: { padding: '5px 12px', fontSize: '0.8rem', background: '#fee2e2', color: '#991b1b', border: '1px solid #fecaca', borderRadius: 6, cursor: 'pointer', fontWeight: 600 },
              onClick: handleDisconnectBot
            }, 'Disconnect Bot')
          )
        ),

        // Circuit breaker alert if tripped
        isTripped && React.createElement('div', { style: { padding: 10, borderRadius: 6, background: '#fef2f2', border: '1px solid #fecaca', color: '#991b1b', marginBottom: 12, fontSize: '0.8rem' } },
          React.createElement('strong', null, '⚠️ Polling Circuit Breaker Tripped: '),
          status.lastError || 'Another RupeeCRM installation is polling with this token. Resolve the conflict and click below to resume.',
          React.createElement('div', { style: { marginTop: 6 } },
            React.createElement('button', {
              className: 'rcrm-btn',
              style: { padding: '4px 10px', fontSize: '0.75rem', background: '#991b1b', color: '#fff', border: 'none', borderRadius: 4, cursor: 'pointer', fontWeight: 600 },
              onClick: handleResumePoller
            }, 'Resume Poller')
          )
        ),

        // Connected Bot Info OR Token Input Form
        isConfigured ? React.createElement('div', { style: { display: 'flex', alignItems: 'center', gap: 10, padding: '10px 14px', borderRadius: 6, background: 'var(--bg-subtle, #f8fafc)', border: '1px solid var(--border-color, #e2e8f0)' } },
          React.createElement('span', { style: { fontSize: '1.3rem' } }, '🤖'),
          React.createElement('div', { style: { flex: 1 } },
            React.createElement('div', { style: { fontWeight: 600, fontSize: '0.9rem' } }, 'Connected Bot: @' + status.botUsername),
            React.createElement('div', { style: { fontSize: '0.78rem', color: 'var(--text-muted)' } },
              'Credentials securely protected in OS Keychain / DPAPI storage. Outbound long-polling active.'
            )
          )
        ) : React.createElement('form', { onSubmit: handleConnectBot, style: { display: 'flex', flexDirection: 'column', gap: 10 } },
          React.createElement('div', null,
            React.createElement('label', { style: { display: 'block', fontSize: '0.8rem', fontWeight: 600, marginBottom: 4 } }, 'Telegram Bot Token:'),
            React.createElement('div', { style: { display: 'flex', gap: 8 } },
              React.createElement('input', {
                type: 'password',
                value: tokenInput,
                onChange: (e) => setTokenInput(e.target.value),
                placeholder: 'e.g. 123456789:ABCdef-XYZ...',
                className: 'rcrm-input',
                style: { flex: 1, padding: '7px 10px', borderRadius: 6, border: '1px solid var(--border-color, #cbd5e1)', fontSize: '0.85rem' }
              }),
              React.createElement('button', {
                type: 'submit',
                disabled: submittingToken || !tokenInput.trim(),
                className: 'rcrm-btn',
                style: { padding: '7px 14px', background: 'var(--primary, #059669)', color: '#fff', border: 'none', borderRadius: 6, fontWeight: 600, cursor: 'pointer', fontSize: '0.85rem' }
              }, submittingToken ? 'Connecting...' : 'Connect & Verify Bot')
            ),
            React.createElement('p', { style: { margin: '4px 0 0 0', fontSize: '0.75rem', color: 'var(--text-muted)' } },
              'Create a bot via @BotFather on Telegram, copy the API token, and paste it here.'
            )
          ),
          tokenError && React.createElement('div', { style: { color: '#dc2626', fontSize: '0.8rem' } }, tokenError)
        )
      ),

      // Card 2: Link Telegram Account (One-Time Pairing Challenge)
      isConfigured && React.createElement('div', { className: 'rcrm-card', style: { padding: '16px 20px', borderRadius: 8, border: '1px solid var(--border-color, #e2e8f0)', background: 'var(--card-bg, #fff)' } },
        React.createElement('div', { style: { marginBottom: 12 } },
          React.createElement('h3', { style: { margin: 0, fontSize: '1rem', fontWeight: 700 } }, '🔑 Link Telegram Account'),
          React.createElement('p', { style: { margin: '2px 0 0 0', fontSize: '0.8rem', color: 'var(--text-muted)' } },
            'Generate a secure single-use pairing code to link an operator phone to RupeeCRM.'
          )
        ),

        // Pairing challenge generator form
        React.createElement('div', { style: { display: 'flex', gap: 10, alignItems: 'flex-end', flexWrap: 'wrap', background: 'var(--bg-subtle, #f8fafc)', padding: 12, borderRadius: 6, border: '1px solid var(--border-color, #e2e8f0)', marginBottom: challengeCode ? 12 : 0 } },
          React.createElement('div', { style: { flex: 1, minWidth: 260 } },
            React.createElement('label', { style: { display: 'block', fontSize: '0.78rem', fontWeight: 600, marginBottom: 4 } }, 'Target Telegram User ID (Optional):'),
            React.createElement('input', {
              type: 'text',
              value: targetUserIdInput,
              onChange: (e) => setTargetUserIdInput(e.target.value),
              placeholder: 'e.g. 123456789 (leave blank for open pairing with desktop approval)',
              className: 'rcrm-input',
              style: { width: '100%', boxSizing: 'border-box', padding: '6px 10px', fontSize: '0.82rem', borderRadius: 5, border: '1px solid var(--border-color, #cbd5e1)' }
            })
          ),
          React.createElement('button', {
            className: 'rcrm-btn',
            disabled: generatingChallenge,
            onClick: handleGenerateChallenge,
            style: { padding: '7px 14px', background: 'var(--primary, #059669)', color: '#fff', border: 'none', borderRadius: 5, fontWeight: 600, cursor: 'pointer', fontSize: '0.82rem', whiteSpace: 'nowrap' }
          }, generatingChallenge ? 'Generating...' : '➕ Generate Pairing Code')
        ),

        // Challenge code banner (if active)
        challengeCode && React.createElement('div', { style: { padding: 14, borderRadius: 6, background: '#f0fdf4', border: '1px solid #bbf7d0', textAlign: 'center' } },
          React.createElement('div', { style: { fontSize: '0.8rem', color: '#166534', fontWeight: 600, marginBottom: 4 } }, 'One-Time Pairing Challenge Code:'),
          React.createElement('div', { style: { display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 10 } },
            React.createElement('span', { style: { fontSize: '1.6rem', fontWeight: 800, letterSpacing: 3, color: '#15803d', fontFamily: 'monospace' } }, challengeCode),
            React.createElement('button', {
              className: 'rcrm-btn',
              onClick: () => {
                navigator.clipboard.writeText('/start ' + challengeCode);
                setCopiedChallenge(true);
                setTimeout(() => setCopiedChallenge(false), 2000);
              },
              style: { padding: '4px 10px', fontSize: '0.75rem', background: '#dcfce7', color: '#166534', border: '1px solid #86efac', borderRadius: 4, cursor: 'pointer', fontWeight: 600 }
            }, copiedChallenge ? '✅ Copied!' : '📋 Copy /start Command')
          ),
          React.createElement('p', { style: { margin: '6px 0 0 0', fontSize: '0.8rem', color: '#166534' } },
            'Open your bot on Telegram and send: ',
            React.createElement('code', { style: { background: '#dcfce7', padding: '2px 6px', borderRadius: 4, fontWeight: 700 } }, '/start ' + challengeCode)
          ),
          challengeExpiry && React.createElement('div', { style: { fontSize: '0.75rem', color: '#15803d', marginTop: 4 } },
            'Expires in: ' + Math.max(0, Math.round((challengeExpiry - Date.now()) / 1000)) + ' seconds'
          )
        )
      ),

      // Card 3: Authorized Telegram Accounts
      isConfigured && React.createElement('div', { className: 'rcrm-card', style: { padding: '16px 20px', borderRadius: 8, border: '1px solid var(--border-color, #e2e8f0)', background: 'var(--card-bg, #fff)' } },
        React.createElement('div', { style: { marginBottom: 12 } },
          React.createElement('h3', { style: { margin: 0, fontSize: '1rem', fontWeight: 700 } }, '👥 Authorized Telegram Accounts'),
          React.createElement('p', { style: { margin: '2px 0 0 0', fontSize: '0.8rem', color: 'var(--text-muted)' } },
            'All authorized accounts operate with uniform full access across all active firms.'
          )
        ),

        // Authorized users table
        React.createElement('div', { style: { width: '100%', overflowX: 'auto' } },
          React.createElement('table', { style: { width: '100%', borderCollapse: 'collapse', fontSize: '0.82rem' } },
            React.createElement('thead', null,
              React.createElement('tr', { style: { borderBottom: '1px solid var(--border-color, #e2e8f0)', textAlign: 'left', color: 'var(--text-muted)', background: 'var(--bg-subtle, #f8fafc)' } },
                React.createElement('th', { style: { padding: '8px 10px', fontWeight: 600 } }, 'User / Display Name'),
                React.createElement('th', { style: { padding: '8px 10px', fontWeight: 600 } }, 'Telegram ID'),
                React.createElement('th', { style: { padding: '8px 10px', fontWeight: 600 } }, 'Linked Date'),
                React.createElement('th', { style: { padding: '8px 10px', fontWeight: 600 } }, 'Status'),
                React.createElement('th', { style: { padding: '8px 10px', textAlign: 'right', fontWeight: 600 } }, 'Actions')
              )
            ),
            React.createElement('tbody', null,
              users.length === 0 ? React.createElement('tr', null,
                React.createElement('td', { colSpan: 5, style: { padding: '20px 10px', textAlign: 'center', color: 'var(--text-muted)', fontSize: '0.82rem' } },
                  'No Telegram accounts linked yet. Click "➕ Generate Pairing Code" above to link an account.'
                )
              ) : users.map(u => React.createElement('tr', { key: u.userId, style: { borderBottom: '1px solid var(--border-color, #f1f5f9)' } },
                React.createElement('td', { style: { padding: '8px 10px', fontWeight: 600 } },
                  u.displayName,
                  u.username && React.createElement('span', { style: { color: 'var(--text-muted)', fontWeight: 400, marginLeft: 6 } }, '@' + u.username)
                ),
                React.createElement('td', { style: { padding: '8px 10px', fontFamily: 'monospace' } }, u.userId),
                React.createElement('td', { style: { padding: '8px 10px', color: 'var(--text-muted)' } },
                  u.authorizedAt ? new Date(u.authorizedAt).toLocaleDateString() : '—'
                ),
                React.createElement('td', { style: { padding: '8px 10px' } },
                  React.createElement('span', {
                    style: {
                      fontSize: '0.72rem',
                      fontWeight: 600,
                      padding: '2px 8px',
                      borderRadius: 999,
                      background: u.status === 'ACTIVE' ? '#dcfce7' : (u.status === 'PENDING_APPROVAL' ? '#fef3c7' : '#fee2e2'),
                      color: u.status === 'ACTIVE' ? '#166534' : (u.status === 'PENDING_APPROVAL' ? '#92400e' : '#991b1b')
                    }
                  }, u.status === 'PENDING_APPROVAL' ? '⏳ PENDING APPROVAL' : u.status)
                ),
                React.createElement('td', { style: { padding: '8px 10px', textAlign: 'right' } },
                  React.createElement('div', { style: { display: 'flex', gap: 6, justifyContent: 'flex-end' } },
                    u.status === 'PENDING_APPROVAL' && React.createElement('button', {
                      className: 'rcrm-btn',
                      onClick: () => handleApproveUser(u.userId),
                      style: { padding: '3px 8px', fontSize: '0.75rem', background: '#dcfce7', color: '#166534', border: '1px solid #86efac', borderRadius: 4, cursor: 'pointer', fontWeight: 600 }
                    }, '✅ Approve'),
                    (u.status === 'ACTIVE' || u.status === 'PENDING_APPROVAL') && React.createElement('button', {
                      className: 'rcrm-btn',
                      onClick: () => handleRevokeUser(u.userId),
                      style: { padding: '3px 8px', fontSize: '0.75rem', background: '#fee2e2', color: '#991b1b', border: '1px solid #fecaca', borderRadius: 4, cursor: 'pointer', fontWeight: 600 }
                    }, u.status === 'PENDING_APPROVAL' ? 'Reject' : 'Revoke Access')
                  )
                )
              ))
            )
          )
        )
      ),

      // Card 4: Compact Diagnostics & Outbound Ping Tool
      isConfigured && React.createElement('div', { className: 'rcrm-card', style: { padding: '16px 20px', borderRadius: 8, border: '1px solid var(--border-color, #e2e8f0)', background: 'var(--card-bg, #fff)' } },
        React.createElement('h3', { style: { margin: '0 0 10px 0', fontSize: '1rem', fontWeight: 700 } }, '🔍 Bot Diagnostics & Live Telemetry'),
        React.createElement('div', { style: { display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(170px, 1fr))', gap: 10, marginBottom: 14 } },
          React.createElement('div', { style: { padding: '8px 12px', borderRadius: 6, background: 'var(--bg-subtle, #f8fafc)', border: '1px solid var(--border-color, #e2e8f0)' } },
            React.createElement('div', { style: { fontSize: '0.72rem', color: 'var(--text-muted)' } }, 'Poller Status'),
            React.createElement('div', { style: { fontWeight: 700, fontSize: '0.88rem', marginTop: 2, color: isRunning ? '#166534' : '#991b1b' } },
              isRunning ? '🟢 Active Long-Polling' : '🔴 Stopped'
            )
          ),
          React.createElement('div', { style: { padding: '8px 12px', borderRadius: 6, background: 'var(--bg-subtle, #f8fafc)', border: '1px solid var(--border-color, #e2e8f0)' } },
            React.createElement('div', { style: { fontSize: '0.72rem', color: 'var(--text-muted)' } }, 'Circuit Breaker'),
            React.createElement('div', { style: { fontWeight: 700, fontSize: '0.88rem', marginTop: 2, color: isTripped ? '#dc2626' : '#166534' } },
              isTripped ? '⚠️ Suspended' : '✅ Normal'
            )
          ),
          React.createElement('div', { style: { padding: '8px 12px', borderRadius: 6, background: 'var(--bg-subtle, #f8fafc)', border: '1px solid var(--border-color, #e2e8f0)' } },
            React.createElement('div', { style: { fontSize: '0.72rem', color: 'var(--text-muted)' } }, 'Last Polled Telegram'),
            React.createElement('div', { style: { fontWeight: 600, fontSize: '0.85rem', marginTop: 2 } },
              status.lastPollAt ? new Date(status.lastPollAt).toLocaleTimeString() : '—'
            )
          ),
          React.createElement('div', { style: { padding: '8px 12px', borderRadius: 6, background: 'var(--bg-subtle, #f8fafc)', border: '1px solid var(--border-color, #e2e8f0)' } },
            React.createElement('div', { style: { fontSize: '0.72rem', color: 'var(--text-muted)' } }, 'Last Ingested Update ID'),
            React.createElement('div', { style: { fontWeight: 600, fontSize: '0.85rem', marginTop: 2, fontFamily: 'monospace' } },
              status.lastUpdateId && status.lastUpdateId > 0 ? '#' + status.lastUpdateId : 'None'
            )
          ),
          React.createElement('div', { style: { padding: '8px 12px', borderRadius: 6, background: 'var(--bg-subtle, #f8fafc)', border: '1px solid var(--border-color, #e2e8f0)' } },
            React.createElement('div', { style: { fontSize: '0.72rem', color: 'var(--text-muted)' } }, 'Last Processed Update'),
            React.createElement('div', { style: { fontWeight: 600, fontSize: '0.85rem', marginTop: 2 } },
              status.lastProcessedAt ? new Date(status.lastProcessedAt).toLocaleTimeString() : '—'
            )
          ),
          React.createElement('div', { style: { padding: '8px 12px', borderRadius: 6, background: 'var(--bg-subtle, #f8fafc)', border: '1px solid var(--border-color, #e2e8f0)' } },
            React.createElement('div', { style: { fontSize: '0.72rem', color: 'var(--text-muted)' } }, 'Last Sent Message'),
            React.createElement('div', { style: { fontWeight: 600, fontSize: '0.85rem', marginTop: 2 } },
              status.lastSentAt ? new Date(status.lastSentAt).toLocaleTimeString() : '—'
            )
          )
        ),
        status.lastError && React.createElement('div', { style: { marginBottom: 12, padding: '8px 10px', borderRadius: 6, background: '#fef2f2', border: '1px solid #fecaca', color: '#991b1b', fontSize: '0.78rem' } },
          React.createElement('strong', null, 'Last Reported Error: '),
          status.lastError
        ),

        // Sub-section: Diagnostic Ping Tool
        React.createElement('div', { style: { borderTop: '1px solid var(--border-color, #e2e8f0)', paddingTop: 12 } },
          React.createElement('div', { style: { fontWeight: 600, fontSize: '0.85rem', marginBottom: 2 } }, '🧪 Outbound Delivery Diagnostic Test'),
          React.createElement('p', { style: { margin: '0 0 8px 0', fontSize: '0.78rem', color: 'var(--text-muted)' } },
            'Send a direct test message to verify Telegram network egress, token authorization, and Markdown parsing.'
          ),
          React.createElement('div', { style: { display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' } },
            React.createElement('input', {
              type: 'text',
              value: testChatIdInput,
              onChange: (e) => setTestChatIdInput(e.target.value),
              placeholder: 'Enter Telegram Chat ID / User ID (e.g. 987654321)',
              className: 'rcrm-input',
              style: { flex: 1, minWidth: 240, padding: '6px 10px', borderRadius: 5, border: '1px solid var(--border-color, #cbd5e1)', fontSize: '0.82rem' }
            }),
            React.createElement('button', {
              className: 'rcrm-btn',
              disabled: testSending || !testChatIdInput.trim(),
              onClick: handleSendTestMessage,
              style: { padding: '6px 14px', background: '#2563eb', color: '#fff', border: 'none', borderRadius: 5, fontWeight: 600, cursor: 'pointer', fontSize: '0.82rem' }
            }, testSending ? 'Sending...' : 'Send Test Message')
          ),
          testResult && React.createElement('div', {
            style: {
              marginTop: 8,
              padding: '6px 10px',
              borderRadius: 5,
              fontSize: '0.8rem',
              background: testResult.success ? '#dcfce7' : '#fee2e2',
              color: testResult.success ? '#166534' : '#991b1b',
              border: '1px solid ' + (testResult.success ? '#bbf7d0' : '#fecaca')
            }
          }, testResult.message)
        )
      )
    );
  }

  window.SettingsTelegram = SettingsTelegram;
})();
