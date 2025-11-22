// js/ui/customer-screen.js
import { customerModule } from "../customer.js";

export const customerScreen = {

  render() {
    return `
      <div class="card">
        <h2>Customers</h2>

        <button class="btn" id="reloadCustomers">Reload</button>

        <div id="customerList"></div>

        <h3>Add Customer</h3>
        <input id="c_name" placeholder="Name"/>
        <input id="c_phone" placeholder="Phone"/>
        <input id="c_email" placeholder="Email"/>
        <input id="c_address" placeholder="Address"/>
        <button class="btn primary" id="saveCustomerBtn">Save</button>
      </div>
    `;
  },

  init() {
    this.load();

    document.getElementById("reloadCustomers").onclick = () => this.load();

    document.getElementById("saveCustomerBtn").onclick = async () => {
      await customerModule.create({
        name: c_name.value,
        phone: c_phone.value,
        email: c_email.value,
        address: c_address.value
      });
      this.load();
    };
  },

  load() {
    const list = customerModule.customers;
    const box = document.getElementById("customerList");
    box.innerHTML = list.map(c => `
      <div class="invoice-list-item">
        <b>${c.name}</b>
        <div>${c.phone}</div>
      </div>
    `).join("");
  }
};
