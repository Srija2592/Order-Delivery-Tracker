import { Component, OnInit, OnDestroy } from '@angular/core';
import { DeliveryService } from '../services/delivery.service';
import { Subscription } from 'rxjs';
import { AuthService } from '../services/auth.service';

interface Order {
  id: string;
  username: string;
  items: string[];
  status: string;
  creationDate: number;
  srcLat: number;
  srcLon: number;
  desLat: number;
  desLon: number;
  curLat: number;
  curLon: number;
  truckId: string | null; // null if unassigned
  assignment: any;
}

@Component({
  selector: 'app-delivery-orders',
  templateUrl: './delivery-orders.component.html',
  styleUrls: ['./delivery-orders.component.css']
})
export class DeliveryOrdersComponent implements OnInit, OnDestroy {
  username: string =''; // Replace with auth service username
  private locationSub: Subscription | null = null;
  assignedOrders: any;

  allOrders: Order[] = []; // All unassigned, undelivered orders
  assignedOrder: Order | null = null; // Order assigned to this user

  // Form fields for updating location
  newCurLat: number | null = null;
  newCurLon: number | null = null;

  constructor(private deliveryService: DeliveryService, private authService: AuthService) {}

  ngOnInit(): void {
    this.username = this.authService.getUserName();
    this.fetchOrders();
    this.subscribeToLocationUpdates();
    this.getAssignedOrders();
  }

  ngOnDestroy(): void {
    if (this.locationSub) {
      this.locationSub.unsubscribe();
    }
  }

  getAssignedOrders() {
    console.log(this.authService.getUserName())
    this.deliveryService.getAssignedOrders(this.authService.getUserName()).subscribe((data)=>{
      this.assignedOrders = data;
    })
  }
  // Fetch all undelivered orders
  fetchOrders(): void {
    this.deliveryService.getNonDeliveredOrders().subscribe({
      next: (orders: Order[]) => {
        // Filter undelivered orders without a truck assigned
        this.allOrders = orders;

        console.log(orders)
        console.log('All non delivered orders:', this.allOrders);
      },
      error: (err) => {
        console.error('Failed to fetch orders', err);
        alert('Failed to load orders. Please try again.');
      }
    });
  }

  // Subscribe to real-time location updates via WebSocket


  private subscribeToLocationUpdates(orderId?: string): void {
    if (this.locationSub) {
        this.locationSub.unsubscribe();
    }
    this.locationSub = this.deliveryService.getLocationUpdates(orderId).subscribe(update => {
        if (this.assignedOrder && this.assignedOrder.id === update.vehicleId) {
            this.assignedOrder.curLat = update.curLat;
            this.assignedOrder.curLon = update.curLon;
            this.assignedOrder.status = update.status;
            console.log('Received live update:', update);
        }
    });
}

  // Assign an order to the current user
  assignOrder(orderId: string): void {
    if (this.assignedOrder) {
      alert('You already have an assigned order. Complete it first.');
      return;
    }

    this.deliveryService.assignOrder(orderId).subscribe({
      next: () => {
        console.log(`Order ${orderId} assigned successfully`);
        this.fetchOrders(); // Refresh the list
      },
      error: (err) => {
        console.error('Failed to assign order', err);
        alert('Failed to assign order: ' + (err.error?.message || 'Unknown error'));
      }
    });
  }
  // Update the assigned order's location
  updateLocation($event:any): void {
    this.deliveryService.updateOrderLocation($event).subscribe({
      next: (response) => console.log('Update location response:', response),
      error: (err) => console.error('Update location error:', err),
    });
  }
}
