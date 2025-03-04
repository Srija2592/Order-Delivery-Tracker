import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { User } from '../models/user';
import { AuthService } from './auth.service';

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

  getUserOrders(username: string): Observable<any[]> {
    return this.http.get<any[]>(`${this.apiUrl}/deliveries/user/orders/${username}`, { headers: this.authService.getHeaders() });
  }

  addOrder(orderData: any): Observable<any> {
    return this.http.post(`${this.apiUrl}/deliveries/orders`, orderData, { headers: this.authService.getHeaders() });
  }

  getOrderDetails(orderId: string): Observable<any> {
    return this.http.get(`${this.apiUrl}/deliveries/orders/${orderId}`, { headers: this.authService.getHeaders() });
  }
}
