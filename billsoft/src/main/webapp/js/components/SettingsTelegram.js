/**
 * RupeeCRM - Telegram Remote Assistant Settings Workspace Component
 * 
 * Provides desktop-controlled bot token configuration, secure OS credential storage,
 * pairing challenge generation, and authorized account management with instant revocation.
 */

(function () {
  'use strict';

  const { useState, useEffect } = React;

  function SettingsTelegram() {
    const isDesktop = typeof window !== 'undefined' && window.rupeeDesktop && window.rupeeDesktop.telegram;

    const [status, setStatus] = useState(null);
    const [loading, setLoading] = useState(true);
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

    // Refresh status and users
    const refreshData = async () => {
      if (!isDesktop) {
        setLoading(false);
        return;
      }
      try {
        const [statRes, userRes] = await Promise.all([
          window.rupeeDesktop.telegram.getStatus(),
          window.rupeeDesktop.telegram.getUsers()
        ]);
        if (statRes && statRes.success) {
          setStatus(statRes);
        }
        if (userRes && userRes.success) {
          setUsers(userRes.users || []);
        }
      } catch (err) {
        console.error('Failed fetching telegram status:', err);
      } finally {
        setLoading(false);
      }
    };

    useEffect(() => {
      refreshData();
      const interval = setInterval(refreshData, 10000);
      return () => clearInterval(interval);
    }, [isDesktop]);

    // Challenge countdown timer
    useEffect(() => {
      if (!challengeExpiry) return;
      const t = setInterval(() => {
        const left = challengeExpiry - Date.now();
        if (left <= 0) {
          setChallengeCode(null);
          setChallengeExpiry(null);
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

    if (loading && !status) {
      return React.createElement('div', { style: { padding: 40, textAlign: 'center', color: 'var(--text-muted)' } },
        'Checking Telegram Assistant status...'
      );
    }

    const isConfigured = status && status.botUsername && status.botUsername.length > 0;
    const isRunning = status && status.isRunning;
    const isTripped = status && status.circuitBreakerTripped;

    return React.createElement('div', { style: { display: 'flex', flexDirection: 'column', gap: 20 } },
      
      // Card 1: Bot Connection & Status
      React.createElement('div', { className: 'rcrm-card', style: { padding: 24, borderRadius: 12, border: '1px solid var(--border-color, #e2e8f0)', background: 'var(--card-bg, #fff)' } },
        React.createElement('div', { style: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 } },
          React.createElement('div', null,
            React.createElement('h3', { style: { margin: 0, fontSize: '1.2rem', fontWeight: 700, display: 'flex', alignItems: 'center', gap: 8 } },
              'Telegram Business Assistant',
              isConfigured && React.createElement('span', {
                style: {
                  fontSize: '0.75rem',
                  fontWeight: 600,
                  padding: '2px 8px',
                  borderRadius: 999,
                  background: isTripped ? '#fee2e2' : (isRunning ? '#dcfce7' : '#fef3c7'),
                  color: isTripped ? '#991b1b' : (isRunning ? '#166534' : '#92400e')
                }
              }, isTripped ? '⚠️ Poller Suspended (Conflict)' : (isRunning ? '🟢 Active Polling' : '🟠 Stopped'))
            ),
            React.createElement('p', { style: { margin: '4px 0 0 0', fontSize: '0.85rem', color: 'var(--text-muted, #64748b)' } },
              'Operate your local RupeeCRM installation remotely from mobile Telegram with zero cloud hosting.'
            )
          ),
          isConfigured && React.createElement('div', { style: { display: 'flex', gap: 8 } },
            React.createElement('button', {
              className: 'rcrm-btn',
              style: { padding: '6px 14px', fontSize: '0.85rem', background: isRunning ? '#fef3c7' : '#dcfce7', color: isRunning ? '#92400e' : '#166534', border: 'none', borderRadius: 6, cursor: 'pointer', fontWeight: 600 },
              onClick: () => handleToggleEnabled(!status.isEnabled)
            }, status.isEnabled ? 'Pause Assistant' : 'Resume Assistant'),
            React.createElement('button', {
              className: 'rcrm-btn',
              style: { padding: '6px 14px', fontSize: '0.85rem', background: '#fee2e2', color: '#991b1b', border: 'none', borderRadius: 6, cursor: 'pointer', fontWeight: 600 },
              onClick: handleDisconnectBot
            }, 'Disconnect Bot')
          )
        ),

        // Circuit breaker alert if tripped
        isTripped && React.createElement('div', { style: { padding: 12, borderRadius: 8, background: '#fef2f2', border: '1px solid #fecaca', color: '#991b1b', marginBottom: 16, fontSize: '0.85rem' } },
          React.createElement('strong', null, '⚠️ Polling Circuit Breaker Tripped: '),
          status.lastError || 'Another RupeeCRM installation is polling with this token. Resolve the conflict and click below to resume.',
          React.createElement('div', { style: { marginTop: 8 } },
            React.createElement('button', {
              className: 'rcrm-btn',
              style: { padding: '4px 10px', fontSize: '0.8rem', background: '#991b1b', color: '#fff', border: 'none', borderRadius: 4, cursor: 'pointer' },
              onClick: handleResumePoller
            }, 'Resume Poller')
          )
        ),

        // Connected Bot Info OR Token Input Form
        isConfigured ? React.createElement('div', { style: { display: 'flex', alignItems: 'center', gap: 12, padding: 12, borderRadius: 8, background: 'var(--bg-subtle, #f8fafc)', border: '1px solid var(--border-color, #e2e8f0)' } },
          React.createElement('span', { style: { fontSize: '1.5rem' } }, '🤖'),
          React.createElement('div', { style: { flex: 1 } },
            React.createElement('div', { style: { fontWeight: 600 } }, 'Connected Bot: @' + status.botUsername),
            React.createElement('div', { style: { fontSize: '0.8rem', color: 'var(--text-muted)' } },
              'Credentials securely protected in OS Keychain / DPAPI storage. Outbound long-polling active.'
            )
          )
        ) : React.createElement('form', { onSubmit: handleConnectBot, style: { display: 'flex', flexDirection: 'column', gap: 12 } },
          React.createElement('div', null,
            React.createElement('label', { style: { display: 'block', fontSize: '0.85rem', fontWeight: 600, marginBottom: 6 } }, 'Telegram Bot Token:'),
            React.createElement('div', { style: { display: 'flex', gap: 8 } },
              React.createElement('input', {
                type: 'password',
                value: tokenInput,
                onChange: (e) => setTokenInput(e.target.value),
                placeholder: 'e.g. 123456789:ABCdef-XYZ...',
                className: 'rcrm-input',
                style: { flex: 1, padding: '8px 12px', borderRadius: 6, border: '1px solid var(--border-color, #cbd5e1)' }
              }),
              React.createElement('button', {
                type: 'submit',
                disabled: submittingToken || !tokenInput.trim(),
                className: 'rcrm-btn',
                style: { padding: '8px 16px', background: 'var(--primary, #059669)', color: '#fff', border: 'none', borderRadius: 6, fontWeight: 600, cursor: 'pointer' }
              }, submittingToken ? 'Connecting...' : 'Connect & Verify Bot')
            ),
            React.createElement('p', { style: { margin: '6px 0 0 0', fontSize: '0.8rem', color: 'var(--text-muted)' } },
              'Create a bot via @BotFather on Telegram, copy the API token, and paste it here.'
            )
          ),
          tokenError && React.createElement('div', { style: { color: '#dc2626', fontSize: '0.85rem' } }, tokenError)
        )
      ),

      // Card 2: Authorized Telegram Accounts & Device Pairing
      isConfigured && React.createElement('div', { className: 'rcrm-card', style: { padding: 24, borderRadius: 12, border: '1px solid var(--border-color, #e2e8f0)', background: 'var(--card-bg, #fff)' } },
        React.createElement('div', { style: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 } },
          React.createElement('div', null,
            React.createElement('h3', { style: { margin: 0, fontSize: '1.1rem', fontWeight: 700 } }, 'Authorized Telegram Accounts'),
            React.createElement('p', { style: { margin: '4px 0 0 0', fontSize: '0.85rem', color: 'var(--text-muted)' } },
              'Pair your owner account or trusted staff members using single-use challenge codes.'
            )
          ),
        // Challenge code generator with optional target user ID
        React.createElement('div', { style: { display: 'flex', gap: 12, alignItems: 'center', marginBottom: 16, flexWrap: 'wrap', background: 'var(--bg-subtle, #f8fafc)', padding: 12, borderRadius: 8, border: '1px solid var(--border-color, #e2e8f0)' } },
          React.createElement('div', { style: { flex: 1, minWidth: 240 } },
            React.createElement('label', { style: { display: 'block', fontSize: '0.8rem', fontWeight: 600, marginBottom: 4 } }, 'Target Telegram User ID (Optional):'),
            React.createElement('input', {
              type: 'text',
              value: targetUserIdInput,
              onChange: (e) => setTargetUserIdInput(e.target.value),
              placeholder: 'e.g. 123456789 (leave blank for open pairing with desktop approval)',
              className: 'rcrm-input',
              style: { width: '100%', padding: '6px 10px', fontSize: '0.85rem', borderRadius: 6, border: '1px solid var(--border-color, #cbd5e1)' }
            })
          ),
          React.createElement('div', { style: { paddingTop: 18 } },
            React.createElement('button', {
              className: 'rcrm-btn',
              disabled: generatingChallenge,
              onClick: handleGenerateChallenge,
              style: { padding: '8px 16px', background: 'var(--primary, #059669)', color: '#fff', border: 'none', borderRadius: 6, fontWeight: 600, cursor: 'pointer' }
            }, generatingChallenge ? 'Generating...' : '➕ Generate Pairing Code')
          )
        ),

        // Challenge code banner (if active)
        challengeCode && React.createElement('div', { style: { padding: 16, borderRadius: 8, background: '#f0fdf4', border: '1px solid #bbf7d0', marginBottom: 16, textAlign: 'center' } },
          React.createElement('div', { style: { fontSize: '0.85rem', color: '#166534', marginBottom: 4 } }, 'One-Time Pairing Challenge Code:'),
          React.createElement('div', { style: { fontSize: '2rem', fontWeight: 800, letterSpacing: 4, color: '#15803d', fontFamily: 'monospace' } }, challengeCode),
          React.createElement('p', { style: { margin: '8px 0 0 0', fontSize: '0.85rem', color: '#166534' } },
            'Open your bot on Telegram and send: ',
            React.createElement('code', { style: { background: '#dcfce7', padding: '2px 6px', borderRadius: 4, fontWeight: 700 } }, '/start ' + challengeCode)
          ),
          challengeExpiry && React.createElement('div', { style: { fontSize: '0.75rem', color: '#15803d', marginTop: 4 } },
            'Expires in: ' + Math.max(0, Math.round((challengeExpiry - Date.now()) / 1000)) + ' seconds'
          )
        ),

        // Authorized users table
        React.createElement('div', { style: { overflowX: 'auto' } },
          React.createElement('table', { style: { width: '100%', borderCollapse: 'collapse', fontSize: '0.85rem' } },
            React.createElement('thead', null,
              React.createElement('tr', { style: { borderBottom: '1px solid var(--border-color, #e2e8f0)', textAlign: 'left', color: 'var(--text-muted)' } },
                React.createElement('th', { style: { padding: '8px 12px' } }, 'User / Display Name'),
                React.createElement('th', { style: { padding: '8px 12px' } }, 'Telegram ID'),
                React.createElement('th', { style: { padding: '8px 12px' } }, 'Linked Date'),
                React.createElement('th', { style: { padding: '8px 12px' } }, 'Status'),
                React.createElement('th', { style: { padding: '8px 12px', textAlign: 'right' } }, 'Actions')
              )
            ),
            React.createElement('tbody', null,
              users.length === 0 ? React.createElement('tr', null,
                React.createElement('td', { colSpan: 5, style: { padding: '16px 12px', textAlign: 'center', color: 'var(--text-muted)' } },
                  'No Telegram accounts linked yet. Click "Link New Telegram Account" to link your phone.'
                )
              ) : users.map(u => React.createElement('tr', { key: u.userId, style: { borderBottom: '1px solid var(--border-color, #f1f5f9)' } },
                React.createElement('td', { style: { padding: '10px 12px', fontWeight: 600 } },
                  u.displayName,
                  u.username && React.createElement('span', { style: { color: 'var(--text-muted)', fontWeight: 400, marginLeft: 6 } }, '@' + u.username)
                ),
                React.createElement('td', { style: { padding: '10px 12px', fontFamily: 'monospace' } }, u.userId),
                React.createElement('td', { style: { padding: '10px 12px', color: 'var(--text-muted)' } },
                  u.authorizedAt ? new Date(u.authorizedAt).toLocaleDateString() : '—'
                ),
                React.createElement('td', { style: { padding: '10px 12px' } },
                  React.createElement('span', {
                    style: {
                      fontSize: '0.75rem',
                      fontWeight: 600,
                      padding: '2px 8px',
                      borderRadius: 999,
                      background: u.status === 'ACTIVE' ? '#dcfce7' : (u.status === 'PENDING_APPROVAL' ? '#fef3c7' : '#fee2e2'),
                      color: u.status === 'ACTIVE' ? '#166534' : (u.status === 'PENDING_APPROVAL' ? '#92400e' : '#991b1b')
                    }
                  }, u.status === 'PENDING_APPROVAL' ? '⏳ PENDING APPROVAL' : u.status)
                ),
                React.createElement('td', { style: { padding: '10px 12px', textAlign: 'right' } },
                  React.createElement('div', { style: { display: 'flex', gap: 6, justifyContent: 'flex-end' } },
                    u.status === 'PENDING_APPROVAL' && React.createElement('button', {
                      className: 'rcrm-btn',
                      onClick: () => handleApproveUser(u.userId),
                      style: { padding: '4px 10px', fontSize: '0.75rem', background: '#dcfce7', color: '#166534', border: 'none', borderRadius: 4, cursor: 'pointer', fontWeight: 600 }
                    }, '✅ Approve'),
                    (u.status === 'ACTIVE' || u.status === 'PENDING_APPROVAL') && React.createElement('button', {
                      className: 'rcrm-btn',
                      onClick: () => handleRevokeUser(u.userId),
                      style: { padding: '4px 10px', fontSize: '0.75rem', background: '#fee2e2', color: '#991b1b', border: 'none', borderRadius: 4, cursor: 'pointer', fontWeight: 600 }
                    }, u.status === 'PENDING_APPROVAL' ? 'Reject' : 'Revoke Access')
                  )
                )
              ))
            )
          )
        )
      ),

      // Card 3: Live Poller Diagnostics & Health Metrics
      isConfigured && React.createElement('div', { className: 'rcrm-card', style: { padding: 24, borderRadius: 12, border: '1px solid var(--border-color, #e2e8f0)', background: 'var(--card-bg, #fff)' } },
        React.createElement('h3', { style: { margin: '0 0 14px 0', fontSize: '1.1rem', fontWeight: 700 } }, '🔍 Bot Diagnostics & Live Metrics'),
        React.createElement('div', { style: { display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: 12 } },
          React.createElement('div', { style: { padding: 12, borderRadius: 8, background: 'var(--bg-subtle, #f8fafc)', border: '1px solid var(--border-color, #e2e8f0)' } },
            React.createElement('div', { style: { fontSize: '0.75rem', color: 'var(--text-muted)' } }, 'Poller Status'),
            React.createElement('div', { style: { fontWeight: 700, fontSize: '0.95rem', marginTop: 4, color: isRunning ? '#166534' : '#991b1b' } },
              isRunning ? '🟢 Active Long-Polling' : '🔴 Stopped'
            )
          ),
          React.createElement('div', { style: { padding: 12, borderRadius: 8, background: 'var(--bg-subtle, #f8fafc)', border: '1px solid var(--border-color, #e2e8f0)' } },
            React.createElement('div', { style: { fontSize: '0.75rem', color: 'var(--text-muted)' } }, 'Circuit Breaker'),
            React.createElement('div', { style: { fontWeight: 700, fontSize: '0.95rem', marginTop: 4, color: isTripped ? '#dc2626' : '#166534' } },
              isTripped ? '⚠️ Suspended' : '✅ Normal'
            )
          ),
          React.createElement('div', { style: { padding: 12, borderRadius: 8, background: 'var(--bg-subtle, #f8fafc)', border: '1px solid var(--border-color, #e2e8f0)' } },
            React.createElement('div', { style: { fontSize: '0.75rem', color: 'var(--text-muted)' } }, 'Last Polled Telegram'),
            React.createElement('div', { style: { fontWeight: 600, fontSize: '0.9rem', marginTop: 4 } },
              status.lastPollAt ? new Date(status.lastPollAt).toLocaleTimeString() : '—'
            )
          ),
          React.createElement('div', { style: { padding: 12, borderRadius: 8, background: 'var(--bg-subtle, #f8fafc)', border: '1px solid var(--border-color, #e2e8f0)' } },
            React.createElement('div', { style: { fontSize: '0.75rem', color: 'var(--text-muted)' } }, 'Last Ingested Update ID'),
            React.createElement('div', { style: { fontWeight: 600, fontSize: '0.9rem', marginTop: 4, fontFamily: 'monospace' } },
              status.lastUpdateId && status.lastUpdateId > 0 ? '#' + status.lastUpdateId : 'None'
            )
          ),
          React.createElement('div', { style: { padding: 12, borderRadius: 8, background: 'var(--bg-subtle, #f8fafc)', border: '1px solid var(--border-color, #e2e8f0)' } },
            React.createElement('div', { style: { fontSize: '0.75rem', color: 'var(--text-muted)' } }, 'Last Processed Update'),
            React.createElement('div', { style: { fontWeight: 600, fontSize: '0.9rem', marginTop: 4 } },
              status.lastProcessedAt ? new Date(status.lastProcessedAt).toLocaleTimeString() : '—'
            )
          ),
          React.createElement('div', { style: { padding: 12, borderRadius: 8, background: 'var(--bg-subtle, #f8fafc)', border: '1px solid var(--border-color, #e2e8f0)' } },
            React.createElement('div', { style: { fontSize: '0.75rem', color: 'var(--text-muted)' } }, 'Last Sent Message'),
            React.createElement('div', { style: { fontWeight: 600, fontSize: '0.9rem', marginTop: 4 } },
              status.lastSentAt ? new Date(status.lastSentAt).toLocaleTimeString() : '—'
            )
          )
        ),
        status.lastError && React.createElement('div', { style: { marginTop: 12, padding: 10, borderRadius: 6, background: '#fef2f2', border: '1px solid #fecaca', color: '#991b1b', fontSize: '0.8rem' } },
          React.createElement('strong', null, 'Last Reported Error: '),
          status.lastError
        )
      ),

      // Card 4: Send Test Message (Diagnostic Tool)
      isConfigured && React.createElement('div', { className: 'rcrm-card', style: { padding: 24, borderRadius: 12, border: '1px solid var(--border-color, #e2e8f0)', background: 'var(--card-bg, #fff)' } },
        React.createElement('h3', { style: { margin: '0 0 8px 0', fontSize: '1.1rem', fontWeight: 700 } }, '🧪 Outbound Delivery Diagnostic Test'),
        React.createElement('p', { style: { margin: '0 0 12px 0', fontSize: '0.85rem', color: 'var(--text-muted)' } },
          'Send a direct test message to verify Telegram network egress, token authorization, and Markdown parsing.'
        ),
        React.createElement('div', { style: { display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' } },
          React.createElement('input', {
            type: 'text',
            value: testChatIdInput,
            onChange: (e) => setTestChatIdInput(e.target.value),
            placeholder: 'Enter Telegram Chat ID / User ID (e.g. 987654321)',
            className: 'rcrm-input',
            style: { flex: 1, minWidth: 260, padding: '8px 12px', borderRadius: 6, border: '1px solid var(--border-color, #cbd5e1)', fontSize: '0.85rem' }
          }),
          React.createElement('button', {
            className: 'rcrm-btn',
            disabled: testSending || !testChatIdInput.trim(),
            onClick: handleSendTestMessage,
            style: { padding: '8px 16px', background: '#2563eb', color: '#fff', border: 'none', borderRadius: 6, fontWeight: 600, cursor: 'pointer' }
          }, testSending ? 'Sending...' : 'Send Test Message')
        ),
        testResult && React.createElement('div', {
          style: {
            marginTop: 10,
            padding: '8px 12px',
            borderRadius: 6,
            fontSize: '0.85rem',
            background: testResult.success ? '#dcfce7' : '#fee2e2',
            color: testResult.success ? '#166534' : '#991b1b',
            border: '1px solid ' + (testResult.success ? '#bbf7d0' : '#fecaca')
          }
        }, testResult.message)
      )
    );
  }

  window.SettingsTelegram = SettingsTelegram;
})();
