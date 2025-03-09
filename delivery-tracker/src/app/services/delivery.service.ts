import { Inject, Injectable, PLATFORM_ID, OnDestroy } from '@angular/core';
import { HttpClient, HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { Subject, Observable, catchError, throwError, timer, interval, lastValueFrom } from 'rxjs';
import { Client, IMessage, StompHeaders } from '@stomp/stompjs';
import { isPlatformBrowser } from '@angular/common';
import SockJS from 'sockjs-client';
import { AuthService } from './auth.service';
import { environment } from '../models/environment';

export interface LocationUpdate {
  srcLat: number;
  srcLon: number;
  curLat: number;
  curLon: number;
  desLat: number;
  desLon: number;
  vehicleId: string;
  status: string;
  timestamp: number;
}

@Injectable({ providedIn: 'root' })
export class DeliveryService implements OnDestroy {
  private stompClient: Client | null = null;
  private locationUpdates = new Subject<LocationUpdate>();
  private retryAttempts = 0;
  private readonly MAX_RETRIES = 3;
  private subscriptions: { [orderId: string]: string } = {};
  private pendingSubscriptions: string[] = []; // Track pending subscriptions

  private readonly wsUserUrl: string;
  private readonly deliveryApi: string;

  constructor(
    private http: HttpClient,
    @Inject(PLATFORM_ID) private platformId: Object,
    private authService: AuthService
  ) {
    this.wsUserUrl = `${environment.userBaseUrl}/api/ws`;
    this.deliveryApi = `${environment.deliveryBaseUrl}/api/deliveries`;

    if (isPlatformBrowser(this.platformId)) {
      this.initializeWebSocketAsync();
    } else {
      console.log('Running on the server - WebSocket disabled');
    }
  }

  ngOnDestroy(): void {
    if (this.stompClient) {
      Object.values(this.subscriptions).forEach(subId => this.stompClient?.unsubscribe(subId));
      this.stompClient.deactivate();
      this.locationUpdates.complete();
      console.log('WebSocket closed and subscriptions cleaned up');
    }
  }

  private async initializeWebSocketAsync(): Promise<void> {
    try {
      const token = await lastValueFrom(this.authService.refreshTokenIfNeeded());
      if (!token) {
        console.error('No valid JWT token, WebSocket aborted');
        this.retryOrFail();
        return;
      }

      const socket = new SockJS(this.wsUserUrl);
      const headers: StompHeaders = { Authorization: `Bearer ${token}` };
      this.stompClient = new Client({
        webSocketFactory: () => socket,
        connectHeaders: headers,
        debug: (str) => console.log('STOMP Debug:', str),
        reconnectDelay: 5000,
        heartbeatIncoming: 10000,
        heartbeatOutgoing: 10000,
      });

      this.stompClient.onConnect = () => {
        console.log('Connected to WebSocket');
        this.retryAttempts = 0;
        // Process any pending subscriptions
        this.pendingSubscriptions.forEach(orderId => this.subscribeToLocationUpdates(orderId));
        this.pendingSubscriptions = [];
      };

      this.stompClient.onWebSocketError = (error) => {
        console.error('WebSocket Error:', error);
        this.retryOrFail();
      };

      this.stompClient.onStompError = (frame) => {
        console.error('STOMP Error:', frame.headers['message'], frame.body);
        this.retryOrFail();
      };

      this.stompClient.activate();
    } catch (err) {
      console.error('Token refresh or initialization failed:', err);
      this.retryOrFail();
    }
  }

  private retryOrFail(): void {
    if (this.retryAttempts < this.MAX_RETRIES) {
      this.retryAttempts++;
      const delay = this.retryAttempts * 5000;
      console.log(`Retrying WebSocket (Attempt ${this.retryAttempts}/${this.MAX_RETRIES})`);
      timer(delay).subscribe(() => this.initializeWebSocketAsync());
    } else {
      console.error('Max retry attempts reached, WebSocket failed');
    }
  }

  getNonDeliveredOrders(): Observable<any> {
    return this.http.get<any>(`${this.deliveryApi}/orders/non-delivered`);
  }

  getAssignedOrders(username: string): Observable<any> {
    return this.http.get<any>(`${this.deliveryApi}/orders/assigned/${username}`);
  }

  assignOrder(orderId: string): Observable<any> {
    const headers = new HttpHeaders({ Authorization: `Bearer ${this.authService.getJwtToken()}` });
    const username = this.authService.getUserName();
    console.log('Assigning order:', orderId, 'to', username);
    return this.http.put(`${this.deliveryApi}/assign/${orderId}/${username}`, {}, { headers });
  }

  addLocation(vehicleId: string, lat: number, lon: number, status: string): Observable<string> {
    return this.http.post<string>(this.deliveryApi, null, {
      params: { vehicleId, lat: lat.toString(), lon: lon.toString(), status },
      headers: this.authService.getHeaders(),
    }).pipe(
      catchError((error: HttpErrorResponse) => {
        console.error('Failed to add location:', error);
        return throwError(() => new Error(`Error: ${error.message}`));
      })
    );
  }


  updateOrderLocation(orderId: string): Observable<any> {
    return this.http.put<any>(`${this.deliveryApi}/location`, orderId);
  }



private subscribeToLocationUpdates(orderId: string): void {
  if (!this.stompClient) {
    console.warn('Stomp client not initialized');
    this.pendingSubscriptions.push(orderId);
    return;
  }

  if (!this.stompClient.connected) {
    console.warn('Stomp client not connected, adding to pending subscriptions');
    this.pendingSubscriptions.push(orderId);

    // Handle pending subscriptions on connection
    this.stompClient.onConnect = () => {
      console.log('Processing pending subscriptions...');
      this.pendingSubscriptions.forEach((id) => this.subscribeToLocationUpdates(id));
      this.pendingSubscriptions = [];
    };
    return;
  }

  const topic = `/api/topic/locations/${orderId}`;

  // Avoid duplicate subscriptions
  if (this.subscriptions[orderId]) {
    console.warn(`Already subscribed to ${topic}`);
    return;
  }

  try {
    const subscription = this.stompClient.subscribe(topic, (message: IMessage) => {
      this.processMessage(message);
    });

    this.subscriptions[orderId] = subscription.id;
    console.log(`Successfully subscribed to ${topic}`);
  } catch (error) {
    console.error('Subscription error for', topic, error);
    this.pendingSubscriptions.push(orderId);
  }
}


  getLocationUpdates(orderId?: string): Observable<LocationUpdate> {
    if (orderId) this.subscribeToLocationUpdates(orderId);
    return this.locationUpdates.asObservable();
  }

  private processMessage(message: IMessage): void {
    console.log('Raw message:', message.body);
    try {
      const [vehicleId, srcLat, srcLon, curLat, curLon, desLat, desLon, status, timestamp] = message.body.split(':');
      const parsedData: LocationUpdate = {
        vehicleId,
        srcLat: parseFloat(srcLat),
        srcLon: parseFloat(srcLon),
        curLat: parseFloat(curLat),
        curLon: parseFloat(curLon),
        desLat: parseFloat(desLat),
        desLon: parseFloat(desLon),
        status,
        timestamp: parseInt(timestamp, 10),
      };
      this.locationUpdates.next(parsedData);
      console.log('Received update:', parsedData);
    } catch (error) {
      console.error('Error processing message:', error, message.body);
    }
  }
}
