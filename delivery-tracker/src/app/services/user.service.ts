import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { catchError, Observable, throwError } from 'rxjs';
import { User } from '../models/user';
import { AuthService } from './auth.service';
import { LocationUpdate } from './delivery.service';

@Injectable({
  providedIn: 'root'
})
export class UserService {
  private apiUrl = 'http://localhost:8080/api';

  constructor(private http: HttpClient, private authService: AuthService) {}

  update(user: Partial<User>, id: string): Observable<User> {
    return this.http.put<User>(`${this.apiUrl}/user/update/${id}`, user, {
      headers: this.authService.getHeaders(),
    });
  }

  getUser(username: string): Observable<User> {
    return this.http.get<User>(`${this.apiUrl}/user/${username}`, { headers: this.authService.getHeaders() });
  }

  getLiveLocation(orderId: string): Observable<LocationUpdate> {
    return this.http.get<LocationUpdate>(`${this.apiUrl}/user/track/${orderId}`, {
      headers: this.authService.getHeaders(),
    }).pipe(
      catchError((error: HttpErrorResponse) => {
        console.error('Error fetching live location:', error);
        return throwError(() => new Error(`Error: ${error.message}`));
      })
    );
  }

  addOrder(orderData: any): Observable<any> {
      return this.http.post(`${this.apiUrl}/user/orders`, orderData, { headers: this.authService.getHeaders() });
    }

    getUserOrders(username: string): Observable<any[]> {
      return this.http.get<any[]>(`${this.apiUrl}/user/orders/username/${username}`, { headers: this.authService.getHeaders() });
    }

    getOrderDetails(orderId: string): Observable<any> {
      return this.http.get(`${this.apiUrl}/user/orders/${orderId}`, { headers: this.authService.getHeaders() });
    }
}
