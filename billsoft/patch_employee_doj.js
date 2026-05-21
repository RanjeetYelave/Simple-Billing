const fs = require('fs');
let code = fs.readFileSync('src/main/java/com/billing/simple/billsoft/entities/Employee.java', 'utf8');

if (!code.includes('dateOfJoining')) {
    code = code.replace('private String role;', `private String role;
    
    @Column(nullable = false)
    private java.time.LocalDate dateOfJoining;`);
    
    code = code.replace('public String getRole() { return role; }', `public java.time.LocalDate getDateOfJoining() { return dateOfJoining; }
    public void setDateOfJoining(java.time.LocalDate dateOfJoining) { this.dateOfJoining = dateOfJoining; }
    public String getRole() { return role; }`);
    
    fs.writeFileSync('src/main/java/com/billing/simple/billsoft/entities/Employee.java', code);
    console.log("Patched Employee.java");
}
