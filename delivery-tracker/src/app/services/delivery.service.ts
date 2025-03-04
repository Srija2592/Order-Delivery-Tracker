import { Inject, Injectable, PLATFORM_ID, OnDestroy } from '@angular/core';
import { HttpClient, HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { Subject, Observable, catchError, throwError, from, lastValueFrom, timer } from 'rxjs';
import { Client, IMessage, StompHeaders } from '@stomp/stompjs';
import { isPlatformBrowser } from '@angular/common';
import SockJS from 'sockjs-client';
import { AuthService } from './auth.service';
import { environment } from '../models/environment';

interface LocationUpdate {
  vehicleId: string;
  lat: number;
  lon: number;
  status: string;
  timestamp: number;
}

@Injectable({ providedIn: 'root' })
export class DeliveryService implements OnDestroy {
  private stompClient: Client | null = null;
  public locationUpdates = new Subject<LocationUpdate>();
  private readonly platformId: Object;
  private readonly wsUrl: string;
  private readonly deliveryApi: string;
  private readonly apiUrl: string;
  private retryAttempts = 0;
  private readonly MAX_RETRIES = 3;

  constructor(
    private http: HttpClient,
    @Inject(PLATFORM_ID) platformId: Object,
    private authService: AuthService
  ) {
    this.platformId = platformId;
    this.wsUrl = `${environment.apiBaseUrl}/api/ws`;
    this.deliveryApi = `${environment.apiBaseUrl}/api/deliveries/location`;
    this.apiUrl = 'http://localhost:8080/api';

    if (isPlatformBrowser(this.platformId)) {
      this.initializeWebSocketAsync();
    } else {
      console.log('Running on the server - WebSocket disabled');
    }
  }

  private async initializeWebSocketAsync(): Promise<void> {
    if (this.stompClient?.active) return;

    const token = this.authService.getJwtToken();
    if (!token) {
      console.error('JWT token is missing!');
      return;
    }

    const socket = new SockJS(this.wsUrl);
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
      this.subscribeToLocationUpdates();
    };

    this.stompClient.activate();
  }

  private retryOrFail(): void {
    if (this.retryAttempts < this.MAX_RETRIES) {
      this.retryAttempts++;
      console.log(`Retrying WebSocket connection (Attempt ${this.retryAttempts}/${this.MAX_RETRIES})`);
      timer(this.retryAttempts * 5000).subscribe(() => this.initializeWebSocketAsync());
    } else {
      console.error('Max retry attempts reached, WebSocket connection failed');
    }
  }

  private subscribeToLocationUpdates(): void {
    this.stompClient?.subscribe('/api/topic/locations', (message: IMessage) => {
      this.processMessage(message);
    });
  }

  getLocationUpdates(): Observable<LocationUpdate> {
    return this.locationUpdates.asObservable();
  }

  private processMessage(message: IMessage): void {
    try {
      if (!message.body) {
        console.warn('Received empty message body');
        return;
      }

      const [vehicleId, lat, lon, status, timestamp] = message.body.split(':');
      if (!vehicleId || isNaN(+lat) || isNaN(+lon) || !status || isNaN(+timestamp)) {
        console.error('Invalid message format:', message.body);
        return;
      }

      const parsedData: LocationUpdate = {
        vehicleId,
        lat: parseFloat(lat),
        lon: parseFloat(lon),
        status,
        timestamp: parseInt(timestamp, 10),
      };

      this.locationUpdates.next(parsedData);
      console.log('Received location update:', parsedData);
    } catch (error) {
      console.error('Error processing message:', error);
    }
  }

  addLocation(vehicleId: string, lat: number, lon: number, status: string): Observable<string> {
    return this.http.post<string>(this.deliveryApi, null, {
      params: { vehicleId, lat: lat.toString(), lon: lon.toString(), status },
      headers: this.authService.getHeaders(),
    }).pipe(
      catchError((error: HttpErrorResponse) => {
        console.error('HTTP Error:', error);
        return throwError(() => new Error(`Failed to add location: ${error.message}`));
      })
    );
  }

  getLiveLocation(orderId: string): Observable<LocationUpdate> {
    return this.http.get<LocationUpdate>(`${this.apiUrl}/deliveries/track/${orderId}`, {
      headers: this.authService.getHeaders(),
    }).pipe(
      catchError((error: HttpErrorResponse) => {
        console.error('Error fetching live location:', error);
        return throwError(() => new Error(`Failed to fetch live location: ${error.message}`));
      })
    );
  }

  ngOnDestroy(): void {
    this.stompClient?.deactivate();
    this.locationUpdates.complete();
    console.log('WebSocket connection closed');
  }
}
