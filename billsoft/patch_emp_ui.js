const fs = require('fs');
let html = fs.readFileSync('src/main/webapp/index.html', 'utf8');

// 1. Fix save payload
html = html.replace(
  'await API.employees.create(formData);',
  'await API.employees.create({ ...formData, firmId: API.firmId });'
);

// 2. Add DOJ to initial state
html = html.replace(
  `const [formData, setFormData] = useState({ name: '', phone: '', role: '', idProofNumber: '', monthlyBaseSalary: '', allowedPaidLeavesPerMonth: 0, isActive: true });`,
  `const [formData, setFormData] = useState({ name: '', phone: '', role: '', idProofNumber: '', monthlyBaseSalary: '', allowedPaidLeavesPerMonth: 0, isActive: true, dateOfJoining: new Date().toISOString().split('T')[0] });`
);

// 3. Update form clear
html = html.replace(
  `setFormData({ name: '', phone: '', role: '', idProofNumber: '', monthlyBaseSalary: '', allowedPaidLeavesPerMonth: 0, isActive: true }); setShowForm(true);`,
  `setFormData({ name: '', phone: '', role: '', idProofNumber: '', monthlyBaseSalary: '', allowedPaidLeavesPerMonth: 0, isActive: true, dateOfJoining: new Date().toISOString().split('T')[0] }); setShowForm(true);`
);

// 4. Add DOJ to Form UI
const oldFormGroup = `React.createElement('div', { className: 'form-group' }, React.createElement('label', { className: 'form-label' }, 'Full Name'), React.createElement('input', { className: 'input', value: formData.name, onChange: e => setFormData({...formData, name: e.target.value}), required: true })),`;
const newFormGroup = `React.createElement('div', { style: { display: 'flex', gap: 16 } },
                React.createElement('div', { className: 'form-group', style: { flex: 2 } }, React.createElement('label', { className: 'form-label' }, 'Full Name'), React.createElement('input', { className: 'input', value: formData.name, onChange: e => setFormData({...formData, name: e.target.value}), required: true })),
                React.createElement('div', { className: 'form-group', style: { flex: 1 } }, React.createElement('label', { className: 'form-label' }, 'Date of Joining'), React.createElement('input', { className: 'input', type: 'date', value: formData.dateOfJoining || '', onChange: e => setFormData({...formData, dateOfJoining: e.target.value}), required: true }))
              ),`;
html = html.replace(oldFormGroup, newFormGroup);

// 5. Add promotion state
const stateInjection = `      const [showPinChange, setShowPinChange] = useState(false);`;
const promotionState = `      const [promotions, setPromotions] = useState([]);
      const [showPromote, setShowPromote] = useState(false);
      const [promData, setPromData] = useState({ effectiveDate: '', type: 'BOTH', newRole: '', newSalary: '', reason: '' });
      const [showPinChange, setShowPinChange] = useState(false);`;
html = html.replace(stateInjection, promotionState);

// 6. Fetch promotions in openEmployee
const fetchInjection = `const sals = await API.employees.getSalaries(emp.id);
          setSalaries(sals);`;
const fetchPromotions = `const sals = await API.employees.getSalaries(emp.id);
          setSalaries(sals);
          const proms = await API.employees.getPromotions(emp.id);
          setPromotions(proms);`;
html = html.replace(fetchInjection, fetchPromotions);

// 7. handlePromote submit
const handlersInjection = `const handleGiveAdvance = async (e) => {`;
const promoteHandler = `const handlePromote = async (e) => {
        e.preventDefault();
        try {
          await API.employees.addPromotion(selectedEmployee.id, {
             ...promData,
             previousRole: selectedEmployee.role,
             previousSalary: selectedEmployee.monthlyBaseSalary
          });
          showToast('Promotion scheduled/applied!', 'success');
          setShowPromote(false);
          openEmployee(selectedEmployee);
          fetchEmployees();
        } catch(e) { showToast('Error saving promotion', 'error'); }
      };
      
      const handleGiveAdvance = async (e) => {`;
html = html.replace(handlersInjection, promoteHandler);

// 8. Add "Promote" Button
const btnInjection = `React.createElement('button', { className: 'btn btn-outline', style: { width: '100%', marginTop: 16 }, onClick: () => { setFormData({...selectedEmployee}); setShowForm(true); } }, 'Edit Profile')`;
const newBtns = `React.createElement('div', { style: { display: 'flex', gap: 8, marginTop: 16 } },
                  React.createElement('button', { className: 'btn btn-outline', style: { flex: 1 }, onClick: () => { setFormData({...selectedEmployee}); setShowForm(true); } }, 'Edit'),
                  React.createElement('button', { className: 'btn btn-primary', style: { flex: 1 }, onClick: () => { setPromData({ effectiveDate: new Date().toISOString().split('T')[0], type: 'BOTH', newRole: selectedEmployee.role || '', newSalary: selectedEmployee.monthlyBaseSalary || '', reason: '' }); setShowPromote(true); } }, 'Promote')
                )`;
html = html.replace(btnInjection, newBtns);

// 9. Add "History & Promotions" tab button
const tabBtnInj = `React.createElement('button', { className: \`btn \${activeTab === 'salary' ? 'btn-primary' : 'btn-ghost'}\`, onClick: () => setActiveTab('salary') }, 'Process Salary')`;
const newTabBtns = `React.createElement('button', { className: \`btn \${activeTab === 'salary' ? 'btn-primary' : 'btn-ghost'}\`, onClick: () => setActiveTab('salary') }, 'Process Salary'),
              React.createElement('button', { className: \`btn \${activeTab === 'history' ? 'btn-primary' : 'btn-ghost'}\`, onClick: () => setActiveTab('history') }, 'History & Timeline')`;
html = html.replace(tabBtnInj, newTabBtns);

// 10. Render Promotions Tab & Modal
const uiInjection = `activeTab === 'salary' && React.createElement('div', { style: { display: 'flex', gap: 20 } },`;
const newUi = `
            activeTab === 'history' && React.createElement('div', { className: 'card' },
                React.createElement('div', { className: 'card-header' }, React.createElement('div', { className: 'card-title' }, 'Timeline & Promotions')),
                React.createElement('div', { className: 'card-body', style: { padding: '20px 0' } },
                  promotions.map(p => React.createElement('div', { key: p.id, style: { display: 'flex', gap: 16, padding: '0 20px', marginBottom: 20 } },
                    React.createElement('div', { style: { minWidth: 100, textAlign: 'right', color: 'var(--text-secondary)', fontWeight: 500 } }, new Date(p.effectiveDate).toLocaleDateString()),
                    React.createElement('div', { style: { position: 'relative', width: 2, backgroundColor: 'var(--border)' } },
                      React.createElement('div', { style: { position: 'absolute', top: 0, left: -4, width: 10, height: 10, borderRadius: '50%', backgroundColor: p.isApplied ? 'var(--primary)' : 'var(--warning)' } })
                    ),
                    React.createElement('div', { style: { paddingBottom: 20 } },
                      React.createElement('div', { style: { fontWeight: 600, fontSize: '1.05rem', color: p.isApplied ? 'inherit' : 'var(--warning)' } }, p.isApplied ? 'Applied Change' : 'Scheduled Future Change'),
                      p.newRole !== p.previousRole && React.createElement('div', null, \`Role: \${p.previousRole || 'None'} ➔ \${p.newRole}\`),
                      p.newSalary !== p.previousSalary && React.createElement('div', null, \`Salary: ₹\${p.previousSalary || 0} ➔ ₹\${p.newSalary}\`),
                      p.reason && React.createElement('div', { style: { color: 'var(--text-secondary)', marginTop: 4, fontStyle: 'italic' } }, \`"\${p.reason}"\`)
                    )
                  )),
                  React.createElement('div', { style: { display: 'flex', gap: 16, padding: '0 20px' } },
                    React.createElement('div', { style: { minWidth: 100, textAlign: 'right', color: 'var(--text-secondary)', fontWeight: 500 } }, selectedEmployee.dateOfJoining ? new Date(selectedEmployee.dateOfJoining).toLocaleDateString() : 'N/A'),
                    React.createElement('div', { style: { position: 'relative', width: 2, backgroundColor: 'transparent' } },
                      React.createElement('div', { style: { position: 'absolute', top: 0, left: -4, width: 10, height: 10, borderRadius: '50%', backgroundColor: 'var(--success)' } })
                    ),
                    React.createElement('div', null,
                      React.createElement('div', { style: { fontWeight: 600, fontSize: '1.05rem' } }, 'Date of Joining')
                    )
                  )
                )
            ),
            
            showPromote && React.createElement('div', { className: 'modal-overlay', onClick: () => setShowPromote(false) },
              React.createElement('div', { className: 'modal', onClick: e => e.stopPropagation() },
                React.createElement('div', { className: 'modal-header' }, React.createElement('div', { className: 'modal-title' }, 'Promote / Update Details'), React.createElement('button', { className: 'modal-close', onClick: () => setShowPromote(false) }, '✕')),
                React.createElement('div', { className: 'modal-body' },
                  React.createElement('form', { onSubmit: handlePromote, style: { display: 'flex', flexDirection: 'column', gap: 16 } },
                    React.createElement('div', { className: 'form-group' }, React.createElement('label', { className: 'form-label' }, 'Effective Date'), React.createElement('input', { className: 'input', type: 'date', value: promData.effectiveDate, onChange: e => setPromData({...promData, effectiveDate: e.target.value}), required: true })),
                    React.createElement('div', { style: { display: 'flex', gap: 16 } },
                      React.createElement('div', { className: 'form-group', style: { flex: 1 } }, React.createElement('label', { className: 'form-label' }, 'New Role'), React.createElement('input', { className: 'input', value: promData.newRole, onChange: e => setPromData({...promData, newRole: e.target.value}) })),
                      React.createElement('div', { className: 'form-group', style: { flex: 1 } }, React.createElement('label', { className: 'form-label' }, 'New Salary (₹)'), React.createElement('input', { className: 'input', type: 'number', value: promData.newSalary, onChange: e => setPromData({...promData, newSalary: e.target.value}) }))
                    ),
                    React.createElement('div', { className: 'form-group' }, React.createElement('label', { className: 'form-label' }, 'Reason / Notes'), React.createElement('input', { className: 'input', placeholder: 'e.g. Annual Appraisal', value: promData.reason, onChange: e => setPromData({...promData, reason: e.target.value}) })),
                    React.createElement('button', { type: 'submit', className: 'btn btn-primary' }, 'Save Promotion / Increment')
                  )
                )
              )
            ),
            
            activeTab === 'salary' && React.createElement('div', { style: { display: 'flex', gap: 20 } },`;
html = html.replace(uiInjection, newUi);

fs.writeFileSync('src/main/webapp/index.html', html);
console.log("Patched index.html");
