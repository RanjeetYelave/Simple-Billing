const fs = require('fs');
let html = fs.readFileSync('src/main/webapp/index.html', 'utf8');

const injectionPoint = html.indexOf('    // ─── MAIN APP ───');
if (injectionPoint === -1) {
    console.log("Could not find injection point");
    process.exit(1);
}

const employeeComponent = `    // ─── EMPLOYEE MANAGEMENT ───
    function EmployeeManager() {
      const [unlocked, setUnlocked] = useState(false);
      const [pin, setPin] = useState('');
      const [employees, setEmployees] = useState([]);
      const [loading, setLoading] = useState(false);
      const [selectedEmployee, setSelectedEmployee] = useState(null);
      const [showForm, setShowForm] = useState(false);
      const [formData, setFormData] = useState({ name: '', phone: '', role: '', idProofNumber: '', monthlyBaseSalary: '', allowedPaidLeavesPerMonth: 0, isActive: true });
      const [activeTab, setActiveTab] = useState('overview');
      
      const [advances, setAdvances] = useState([]);
      const [salaries, setSalaries] = useState([]);
      const [advanceAmount, setAdvanceAmount] = useState('');
      const [advanceDesc, setAdvanceDesc] = useState('');
      
      const [salaryMonth, setSalaryMonth] = useState('');
      const [daysAbsent, setDaysAbsent] = useState(0);
      const [bonus, setBonus] = useState(0);
      const [advanceDeduct, setAdvanceDeduct] = useState(0);
      const [daysInMonth, setDaysInMonth] = useState(30);
      
      const [showPinChange, setShowPinChange] = useState(false);
      const [oldPin, setOldPin] = useState('');
      const [newPin, setNewPin] = useState('');

      const fetchEmployees = async () => {
        setLoading(true);
        try {
          const data = await API.employees.getAll();
          setEmployees(data);
        } catch(e) { showToast('Error fetching employees', 'error'); }
        setLoading(false);
      };

      const handleUnlock = async (e) => {
        e.preventDefault();
        try {
          const res = await API.employees.verifyPin(pin);
          if (res.valid) { setUnlocked(true); fetchEmployees(); }
          else showToast('Incorrect PIN', 'error');
        } catch(e) { showToast('Error verifying PIN', 'error'); }
      };
      
      const handleChangePin = async (e) => {
        e.preventDefault();
        try {
          await API.employees.changePin(oldPin, newPin);
          showToast('PIN changed successfully', 'success');
          setShowPinChange(false);
          setOldPin(''); setNewPin('');
        } catch(e) { showToast(e.message || 'Error changing PIN', 'error'); }
      };

      const handleSaveEmployee = async (e) => {
        e.preventDefault();
        setLoading(true);
        try {
          if (selectedEmployee && selectedEmployee.id) {
            await API.employees.update(selectedEmployee.id, formData);
            showToast('Employee updated', 'success');
          } else {
            await API.employees.create(formData);
            showToast('Employee created', 'success');
          }
          setShowForm(false);
          setSelectedEmployee(null);
          fetchEmployees();
        } catch(e) { showToast('Error saving employee', 'error'); }
        setLoading(false);
      };

      const openEmployee = async (emp) => {
        setSelectedEmployee(emp);
        setActiveTab('overview');
        setShowForm(false);
        try {
          const advs = await API.employees.getAdvances(emp.id);
          setAdvances(advs);
          const sals = await API.employees.getSalaries(emp.id);
          setSalaries(sals);
        } catch(e) { showToast('Error loading details', 'error'); }
      };
      
      const handleGiveAdvance = async (e) => {
        e.preventDefault();
        if(!advanceAmount) return;
        try {
          await API.employees.addAdvance(selectedEmployee.id, { amount: parseFloat(advanceAmount), description: advanceDesc });
          showToast('Advance recorded', 'success');
          setAdvanceAmount(''); setAdvanceDesc('');
          openEmployee(selectedEmployee);
          fetchEmployees();
        } catch(e) { showToast('Error recording advance', 'error'); }
      };

      const handleProcessSalary = async (e) => {
        e.preventDefault();
        if(!salaryMonth) { showToast('Select month', 'error'); return; }
        
        let dAbsent = parseInt(daysAbsent) || 0;
        let allowed = selectedEmployee.allowedPaidLeavesPerMonth || 0;
        let pUsed = Math.min(dAbsent, allowed);
        let uLeaves = Math.max(0, dAbsent - allowed);
        
        let perDay = selectedEmployee.monthlyBaseSalary / daysInMonth;
        let leaveDed = uLeaves * perDay;
        let bon = parseFloat(bonus) || 0;
        let advD = parseFloat(advanceDeduct) || 0;
        
        let net = selectedEmployee.monthlyBaseSalary - leaveDed + bon - advD;
        
        try {
          await API.employees.processSalary(selectedEmployee.id, {
            monthYear: salaryMonth,
            baseSalaryAtTime: selectedEmployee.monthlyBaseSalary,
            daysAbsent: dAbsent,
            paidLeavesUsed: pUsed,
            unpaidLeaves: uLeaves,
            leaveDeductionAmount: leaveDed,
            bonusAmount: bon,
            advanceDeducted: advD,
            netPaid: net
          });
          showToast('Salary processed!', 'success');
          setSalaryMonth(''); setDaysAbsent(0); setBonus(0); setAdvanceDeduct(0);
          openEmployee(selectedEmployee);
          fetchEmployees();
        } catch(e) { showToast('Error processing salary', 'error'); }
      };

      if (!unlocked) {
        return React.createElement('div', { className: 'card', style: { maxWidth: 400, margin: '60px auto' } },
          React.createElement('div', { className: 'card-header' }, React.createElement('div', { className: 'card-title', style: {textAlign:'center'} }, '🔐 Employee Management')),
          React.createElement('div', { className: 'card-body' },
            React.createElement('form', { onSubmit: handleUnlock, style: { display: 'flex', flexDirection: 'column', gap: 16 } },
              React.createElement('p', { style:{textAlign:'center', color:'var(--text-secondary)'} }, 'Enter your 4-digit PIN to access this module. Default is 0000.'),
              React.createElement('input', { className: 'input', type: 'password', maxLength: 4, placeholder: 'PIN', value: pin, onChange: e => setPin(e.target.value), style: { textAlign: 'center', fontSize: '2rem', letterSpacing: '0.5em' } }),
              React.createElement('button', { type: 'submit', className: 'btn btn-primary', style: { width: '100%' } }, 'Unlock')
            )
          )
        );
      }

      return React.createElement('div', null,
        React.createElement('div', { style: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 } },
          React.createElement('h2', { style: { fontSize: '1.5rem', fontWeight: 600 } }, 'Employee Management'),
          React.createElement('div', { style: { display: 'flex', gap: 10 } },
            React.createElement('button', { className: 'btn btn-outline', onClick: () => setShowPinChange(true) }, 'Change PIN'),
            !selectedEmployee && !showForm && React.createElement('button', { className: 'btn btn-primary', onClick: () => { setSelectedEmployee(null); setFormData({ name: '', phone: '', role: '', idProofNumber: '', monthlyBaseSalary: '', allowedPaidLeavesPerMonth: 0, isActive: true }); setShowForm(true); } }, '+ New Employee'),
            (selectedEmployee || showForm) && React.createElement('button', { className: 'btn btn-outline', onClick: () => { setSelectedEmployee(null); setShowForm(false); } }, 'Back to List')
          )
        ),
        
        showPinChange && React.createElement('div', { className: 'card', style: { maxWidth: 400, marginBottom: 20 } },
          React.createElement('div', { className: 'card-body' },
            React.createElement('form', { onSubmit: handleChangePin, style: { display: 'flex', flexDirection: 'column', gap: 12 } },
              React.createElement('input', { className: 'input', type: 'password', placeholder: 'Old PIN', value: oldPin, onChange: e => setOldPin(e.target.value) }),
              React.createElement('input', { className: 'input', type: 'password', placeholder: 'New PIN', value: newPin, onChange: e => setNewPin(e.target.value) }),
              React.createElement('div', { style: { display: 'flex', gap: 10 } },
                React.createElement('button', { type: 'button', className: 'btn btn-outline', style: { flex: 1 }, onClick: () => setShowPinChange(false) }, 'Cancel'),
                React.createElement('button', { type: 'submit', className: 'btn btn-primary', style: { flex: 1 } }, 'Update PIN')
              )
            )
          )
        ),
        
        !selectedEmployee && !showForm && React.createElement('div', { className: 'card' },
          React.createElement('div', { className: 'table-container' },
            React.createElement('table', { className: 'table' },
              React.createElement('thead', null, React.createElement('tr', null, React.createElement('th', null, 'Name'), React.createElement('th', null, 'Role'), React.createElement('th', null, 'Base Salary'), React.createElement('th', null, 'Advance Balance'), React.createElement('th', null, 'Status'))),
              React.createElement('tbody', null,
                employees.map(emp => React.createElement('tr', { key: emp.id, style: { cursor: 'pointer' }, onClick: () => openEmployee(emp) },
                  React.createElement('td', { style: { fontWeight: 500 } }, emp.name),
                  React.createElement('td', { style: { color: 'var(--text-secondary)' } }, emp.role || '-'),
                  React.createElement('td', null, formatCurrency(emp.monthlyBaseSalary)),
                  React.createElement('td', { style: { color: emp.currentAdvanceBalance > 0 ? 'var(--danger)' : 'inherit' } }, formatCurrency(emp.currentAdvanceBalance)),
                  React.createElement('td', null, emp.isActive ? React.createElement('span', { className: 'badge badge-success' }, 'Active') : React.createElement('span', { className: 'badge' }, 'Inactive'))
                ))
              )
            )
          )
        ),
        
        showForm && React.createElement('div', { className: 'card', style: { maxWidth: 600 } },
          React.createElement('div', { className: 'card-header' }, React.createElement('div', { className: 'card-title' }, selectedEmployee ? 'Edit Employee' : 'New Employee')),
          React.createElement('div', { className: 'card-body' },
            React.createElement('form', { onSubmit: handleSaveEmployee, style: { display: 'flex', flexDirection: 'column', gap: 16 } },
              React.createElement('div', { className: 'form-group' }, React.createElement('label', { className: 'form-label' }, 'Full Name'), React.createElement('input', { className: 'input', value: formData.name, onChange: e => setFormData({...formData, name: e.target.value}), required: true })),
              React.createElement('div', { style: { display: 'flex', gap: 16 } },
                React.createElement('div', { className: 'form-group', style: { flex: 1 } }, React.createElement('label', { className: 'form-label' }, 'Role'), React.createElement('input', { className: 'input', value: formData.role, onChange: e => setFormData({...formData, role: e.target.value}) })),
                React.createElement('div', { className: 'form-group', style: { flex: 1 } }, React.createElement('label', { className: 'form-label' }, 'Phone'), React.createElement('input', { className: 'input', value: formData.phone, onChange: e => setFormData({...formData, phone: e.target.value}) }))
              ),
              React.createElement('div', { style: { display: 'flex', gap: 16 } },
                React.createElement('div', { className: 'form-group', style: { flex: 1 } }, React.createElement('label', { className: 'form-label' }, 'Monthly Base Salary (₹)'), React.createElement('input', { className: 'input', type: 'number', value: formData.monthlyBaseSalary, onChange: e => setFormData({...formData, monthlyBaseSalary: e.target.value}), required: true })),
                React.createElement('div', { className: 'form-group', style: { flex: 1 } }, React.createElement('label', { className: 'form-label' }, 'Allowed Paid Leaves/Mo'), React.createElement('input', { className: 'input', type: 'number', value: formData.allowedPaidLeavesPerMonth, onChange: e => setFormData({...formData, allowedPaidLeavesPerMonth: e.target.value}), required: true }))
              ),
              React.createElement('div', { className: 'form-group' }, React.createElement('label', { className: 'form-label' }, 'ID Proof Number (Aadhar/PAN)'), React.createElement('input', { className: 'input', value: formData.idProofNumber, onChange: e => setFormData({...formData, idProofNumber: e.target.value}) })),
              React.createElement('label', { style: { display: 'flex', alignItems: 'center', gap: 8 } }, React.createElement('input', { type: 'checkbox', checked: formData.isActive, onChange: e => setFormData({...formData, isActive: e.target.checked}) }), 'Active Employee'),
              React.createElement('button', { type: 'submit', className: 'btn btn-primary', disabled: loading }, 'Save Employee')
            )
          )
        ),
        
        selectedEmployee && !showForm && React.createElement('div', { style: { display: 'flex', gap: 20, alignItems: 'flex-start' } },
          React.createElement('div', { style: { width: '300px', flexShrink: 0, display: 'flex', flexDirection: 'column', gap: 20 } },
            React.createElement('div', { className: 'card' },
              React.createElement('div', { className: 'card-body', style: { textAlign: 'center' } },
                React.createElement('div', { style: { width: 80, height: 80, borderRadius: '50%', backgroundColor: 'var(--primary)', color: 'white', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '2rem', margin: '0 auto 16px', fontWeight: 600 } }, selectedEmployee.name.charAt(0).toUpperCase()),
                React.createElement('h3', { style: { fontSize: '1.2rem', margin: '0 0 4px 0' } }, selectedEmployee.name),
                React.createElement('p', { style: { color: 'var(--text-secondary)', margin: 0, fontSize: '0.9rem' } }, selectedEmployee.role || 'Employee'),
                React.createElement('button', { className: 'btn btn-outline', style: { width: '100%', marginTop: 16 }, onClick: () => { setFormData({...selectedEmployee}); setShowForm(true); } }, 'Edit Profile')
              )
            ),
            React.createElement('div', { className: 'card' },
              React.createElement('div', { className: 'card-header' }, React.createElement('div', { className: 'card-title' }, 'Financials')),
              React.createElement('div', { className: 'card-body' },
                React.createElement('div', { style: { display: 'flex', justifyContent: 'space-between', marginBottom: 12, paddingBottom: 12, borderBottom: '1px solid var(--border)' } }, React.createElement('span', { style: { color: 'var(--text-secondary)' } }, 'Base Salary'), React.createElement('span', { style: { fontWeight: 500 } }, formatCurrency(selectedEmployee.monthlyBaseSalary))),
                React.createElement('div', { style: { display: 'flex', justifyContent: 'space-between', marginBottom: 12 } }, React.createElement('span', { style: { color: 'var(--text-secondary)' } }, 'Advance Owed'), React.createElement('span', { style: { fontWeight: 600, color: 'var(--danger)' } }, formatCurrency(selectedEmployee.currentAdvanceBalance)))
              )
            )
          ),
          
          React.createElement('div', { style: { flex: 1, display: 'flex', flexDirection: 'column', gap: 20 } },
            React.createElement('div', { style: { display: 'flex', gap: 10, borderBottom: '1px solid var(--border)', paddingBottom: 10 } },
              React.createElement('button', { className: \`btn \${activeTab === 'overview' ? 'btn-primary' : 'btn-ghost'}\`, onClick: () => setActiveTab('overview') }, 'Advances & Ledger'),
              React.createElement('button', { className: \`btn \${activeTab === 'salary' ? 'btn-primary' : 'btn-ghost'}\`, onClick: () => setActiveTab('salary') }, 'Process Salary')
            ),
            
            activeTab === 'overview' && React.createElement('div', { style: { display: 'flex', gap: 20 } },
              React.createElement('div', { className: 'card', style: { flex: 1 } },
                React.createElement('div', { className: 'card-header' }, React.createElement('div', { className: 'card-title' }, 'Give Advance')),
                React.createElement('div', { className: 'card-body' },
                  React.createElement('form', { onSubmit: handleGiveAdvance, style: { display: 'flex', flexDirection: 'column', gap: 12 } },
                    React.createElement('input', { className: 'input', type: 'number', placeholder: 'Amount (₹)', value: advanceAmount, onChange: e => setAdvanceAmount(e.target.value), required: true }),
                    React.createElement('input', { className: 'input', placeholder: 'Description', value: advanceDesc, onChange: e => setAdvanceDesc(e.target.value) }),
                    React.createElement('button', { type: 'submit', className: 'btn btn-primary' }, 'Record Advance')
                  )
                )
              ),
              React.createElement('div', { className: 'card', style: { flex: 2 } },
                React.createElement('div', { className: 'card-header' }, React.createElement('div', { className: 'card-title' }, 'Advance Ledger')),
                React.createElement('div', { className: 'table-container' },
                  React.createElement('table', { className: 'table', style: { fontSize: '0.9rem' } },
                    React.createElement('thead', null, React.createElement('tr', null, React.createElement('th', null, 'Date'), React.createElement('th', null, 'Description'), React.createElement('th', { style: { textAlign: 'right' } }, 'Amount'))),
                    React.createElement('tbody', null,
                      advances.map(a => React.createElement('tr', { key: a.id },
                        React.createElement('td', null, new Date(a.date).toLocaleDateString()),
                        React.createElement('td', null, a.description),
                        React.createElement('td', { style: { textAlign: 'right', color: a.amount > 0 ? 'var(--danger)' : 'var(--success)' } }, a.amount > 0 ? \`+\${formatCurrency(a.amount)}\` : formatCurrency(a.amount))
                      )),
                      advances.length === 0 && React.createElement('tr', null, React.createElement('td', { colSpan: 3, style: { textAlign: 'center', color: 'var(--text-secondary)' } }, 'No advance records found.'))
                    )
                  )
                )
              )
            ),
            
            activeTab === 'salary' && React.createElement('div', { style: { display: 'flex', gap: 20 } },
              React.createElement('div', { className: 'card', style: { flex: 1.5 } },
                React.createElement('div', { className: 'card-header' }, React.createElement('div', { className: 'card-title' }, 'Process Monthly Salary')),
                React.createElement('div', { className: 'card-body' },
                  React.createElement('form', { onSubmit: handleProcessSalary, style: { display: 'flex', flexDirection: 'column', gap: 16 } },
                    React.createElement('div', { style: { display: 'flex', gap: 12 } },
                      React.createElement('div', { className: 'form-group', style: { flex: 1 } }, React.createElement('label', { className: 'form-label' }, 'Month/Year'), React.createElement('input', { className: 'input', placeholder: 'e.g. 05-2026', value: salaryMonth, onChange: e => setSalaryMonth(e.target.value), required: true })),
                      React.createElement('div', { className: 'form-group', style: { flex: 1 } }, React.createElement('label', { className: 'form-label' }, 'Days in Month'), React.createElement('input', { className: 'input', type: 'number', value: daysInMonth, onChange: e => setDaysInMonth(e.target.value), required: true }))
                    ),
                    React.createElement('div', { className: 'form-group' }, React.createElement('label', { className: 'form-label' }, 'Days Absent'), React.createElement('input', { className: 'input', type: 'number', value: daysAbsent, onChange: e => setDaysAbsent(e.target.value) })),
                    React.createElement('div', { className: 'form-group' }, React.createElement('label', { className: 'form-label' }, 'Bonus / Overtime (₹)'), React.createElement('input', { className: 'input', type: 'number', value: bonus, onChange: e => setBonus(e.target.value) })),
                    React.createElement('div', { className: 'form-group' },
                      React.createElement('label', { className: 'form-label', style: { display: 'flex', justifyContent: 'space-between' } }, React.createElement('span', null, 'Deduct from Advance (₹)'), React.createElement('span', { style: { color: 'var(--danger)', fontSize: '0.85rem' } }, \`Max: \${formatCurrency(selectedEmployee.currentAdvanceBalance)}\`)),
                      React.createElement('input', { className: 'input', type: 'number', max: selectedEmployee.currentAdvanceBalance, value: advanceDeduct, onChange: e => setAdvanceDeduct(e.target.value) })
                    ),
                    React.createElement('button', { type: 'submit', className: 'btn btn-primary', style: { marginTop: 10 } }, 'Mark as Paid')
                  )
                )
              ),
              React.createElement('div', { className: 'card', style: { flex: 1 } },
                React.createElement('div', { className: 'card-header' }, React.createElement('div', { className: 'card-title' }, 'Salary History')),
                React.createElement('div', { className: 'card-body', style: { padding: 0 } },
                  salaries.map(s => React.createElement('div', { key: s.id, style: { padding: '12px 16px', borderBottom: '1px solid var(--border)' } },
                    React.createElement('div', { style: { display: 'flex', justifyContent: 'space-between', marginBottom: 4 } }, React.createElement('strong', null, s.monthYear), React.createElement('strong', { style: { color: 'var(--success)' } }, formatCurrency(s.netPaid))),
                    React.createElement('div', { style: { fontSize: '0.85rem', color: 'var(--text-secondary)' } }, \`Paid on \${new Date(s.paymentDate).toLocaleDateString()}\`),
                    React.createElement('div', { style: { fontSize: '0.85rem', color: 'var(--text-secondary)', display: 'flex', gap: 8, marginTop: 4 } },
                      s.leaveDeductionAmount > 0 && React.createElement('span', { className: 'badge', style: { backgroundColor: 'var(--danger)', color: 'white' } }, \`-\${formatCurrency(s.leaveDeductionAmount)} Absences\`),
                      s.bonusAmount > 0 && React.createElement('span', { className: 'badge', style: { backgroundColor: 'var(--success)', color: 'white' } }, \`+\${formatCurrency(s.bonusAmount)} Bonus\`),
                      s.advanceDeducted > 0 && React.createElement('span', { className: 'badge' }, \`-\${formatCurrency(s.advanceDeducted)} Adv\`)
                    )
                  )),
                  salaries.length === 0 && React.createElement('div', { style: { padding: 20, textAlign: 'center', color: 'var(--text-secondary)' } }, 'No salary history.')
                )
              )
            )
          )
        )
      );
    }

`;

fs.writeFileSync('src/main/webapp/index.html', html.substring(0, injectionPoint) + employeeComponent + html.substring(injectionPoint));
