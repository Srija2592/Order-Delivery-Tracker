import { Component, OnInit } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { Router } from '@angular/router';
import { Role, User } from '../models/user';
import { AuthService } from '../services/auth.service';
import { UserService } from '../services/user.service';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';

@Component({
  selector: 'app-profile',
  templateUrl: './profile.component.html',
  styleUrls: ['./profile.component.css'],
})
export class ProfileComponent implements OnInit {
  user: User = {
    username: '', name: '', email: '', role: Role.USER,
    id: 0
  };
  isNewOrder: boolean = false;
  orders: any[] = [];
  displayedColumns: string[] = ['id', 'source', 'destination', 'status', 'actions'];

  private userSubject = new BehaviorSubject<User>(this.user);
  role: string[] = [];
  orderForm: FormGroup;

  constructor(
    private authService: AuthService,
    private userService: UserService,
    private router: Router,
    private fb: FormBuilder
  ) {
    this.orderForm = this.fb.group({
      id: ['', Validators.required],
      items: ['', Validators.required],
      truckId: ['', Validators.required],
      desLat: ['', Validators.required],
      desLon: ['', Validators.required]
    });
  }

  ngOnInit(): void {
    const username = this.authService.getUserName();
    if (username) {
      this.userService.getUser(username).subscribe({
        next: (userData) => {
          this.user = userData;
          this.userSubject.next(userData);
        },
        error: (err) => console.error('Error fetching user:', err)
      });
      this.userService.getUserOrders(username).subscribe({
        next: (ordersData) => {
          console.log('Fetched orders:', ordersData); // Debug log
          this.orders = ordersData;
        },
        error: (err) => console.error('Error fetching orders:', err)
      });
      this.role = this.authService.getUserRole();
    }
    this.authService.isLoggedIn().subscribe(isLoggedIn => {
      if (!isLoggedIn) this.router.navigate(['/unauthorized']);
    });
  }

  createNewOrder() {
    this.isNewOrder = !this.isNewOrder;
  }

  onSubmit() {
    if (this.orderForm.valid) {
      const orderData = {
        id: this.orderForm.value.id,
        username: this.authService.getUserName(),
        items: this.orderForm.value.items.split(',').map((item: string) => item.trim()),
        creationDate: Date.now(),
        truckId: this.orderForm.value.truckId,
        desLat: this.orderForm.value.desLat,
        desLon: this.orderForm.value.desLon
      };

      this.userService.addOrder(orderData).subscribe({
        next: (response) => {
          console.log('Order added:', response);
          this.orderForm.reset();
          this.ngOnInit(); // Refresh orders
        },
        error: (err) => console.error('Error adding order:', err)
      });
    }
  }

  viewOrderDetails(orderId: string) {
    this.router.navigate(['/order-detail', orderId]);
  }

  trackCurrentOrder(orderId: string) {
    this.router.navigate(['/tracker'], { queryParams: { orderId } });
  }
}
