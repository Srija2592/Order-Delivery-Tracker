import { HttpClient, HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { EventEmitter, Injectable, Output } from '@angular/core';
import { LocalStorageService } from 'ngx-webstorage';
import { BehaviorSubject, Observable, of, throwError } from 'rxjs';
import { catchError, map, tap } from 'rxjs/operators';
import { SignupRequest } from '../auth/signup/signuprequest';
import { LoginRequest } from '../auth/login/loginrequest';
import { LoginResponse, RefreshTokenPayload } from '../auth/login/loginresponse';

@Injectable({
  providedIn: 'root',
})
export class AuthService {
  @Output() loggedInn: EventEmitter<boolean> = new EventEmitter();
  @Output() username: EventEmitter<string> = new EventEmitter();
  userModel: LoginResponse = {
    authenticationToken: '',
    refreshToken: '',
    expiresAt: new Date(),
    username: '',
    roles: [],
  };
  loginDetails = new BehaviorSubject<LoginResponse>(this.userModel);
  loginStatus = new BehaviorSubject<boolean>(false);
  loginResponse!: LoginResponse;
  userDetails: LoginResponse = {
    authenticationToken: '',
    refreshToken: '',
    expiresAt: new Date(),
    username: '',
    roles: [],
  };

  refreshTokenPayload = new BehaviorSubject<RefreshTokenPayload>({
    username: '',
    refreshToken: '',
  });

  private apiUrl = 'http://localhost:8080/api/auth';

  user = new BehaviorSubject<LoginResponse>(this.userModel);

  constructor(private http: HttpClient, private localstorage: LocalStorageService) {}

  signup(signupRequest: SignupRequest) {
    return this.http.post('http://localhost:8080/api/auth/signup', signupRequest, { responseType: 'text' });
  }

  login(loginRequest: LoginRequest): Observable<LoginResponse> {
    return this.http.post<LoginResponse>('http://localhost:8080/api/auth/login', loginRequest).pipe(
      map((data) => {
        this.localstorage.store('authenticationToken', data.authenticationToken);
        this.localstorage.store('username', data.username);
        this.localstorage.store('refreshToken', data.refreshToken);
        this.localstorage.store('expiresAt', data.expiresAt);
        this.localstorage.store('roles', data.roles);
        this.isLoggedIn().subscribe((d) => this.loggedInn.emit(d));
        this.username.emit(data.username);
        this.user.next(data);
        return data;
      }),
      catchError((error: HttpErrorResponse) => {
        console.error('Login failed:', error);
        return throwError(() => new Error('Login failed: ' + error.message));
      })
    );
  }

  getUserName() {
    return this.localstorage.retrieve('username');
  }

  isLoggedIn(): Observable<boolean> {
    const token = this.getJwtToken();
    if (!token) return of(false);
    return this.http.get<boolean>(`${this.apiUrl}/loggedIn`, {
      headers: { Authorization: `Bearer ${token}` },
    }).pipe(
      catchError(() => of(false))
    );
  }

  getRefreshToken() {
    return this.localstorage.retrieve('refreshToken') || '';
  }

  logout() {
    this.http.post('http://localhost:8080/api/auth/logout', this.refreshTokenPayload.value, { responseType: 'text' })
      .subscribe({
        next: (data) => console.log(data),
        error: (error) => console.error(error)
      });
    this.localstorage.clear('authenticationToken');
    this.localstorage.clear('username');
    this.localstorage.clear('refreshToken');
    this.localstorage.clear('expiresAt');
    this.loggedInn.emit(false);
  }

  getJwtToken(): string {
    return this.localstorage.retrieve('authenticationToken') || '';
  }

  public refreshTokenIfNeeded(): Observable<string> {
    const token = this.getJwtToken();
    if (!token || new Date(this.localstorage.retrieve('expiresAt')) < new Date()) {
      return this.refreshToken().pipe(
        tap((response: LoginResponse) => {
          this.localstorage.store('authenticationToken', response.authenticationToken);
          this.localstorage.store('refreshToken', response.refreshToken);
          this.localstorage.store('expiresAt', response.expiresAt);
          this.user.next(response);
        }),
        map(() => this.getJwtToken()),
        catchError((err) => {
          console.error('Refresh failed:', err);
          this.logout();
          return of('');
        })
      );
    }
    return of(token);
  }

  getUserRole() {
    return this.localstorage.retrieve('roles');
  }

  refreshToken(): Observable<LoginResponse> {
    return this.http.post<LoginResponse>('http://localhost:8080/api/auth/refresh/token', this.refreshTokenPayload.value).pipe(
      tap((response) => {
        this.localstorage.store('authenticationToken', response.authenticationToken);
        this.localstorage.store('refreshToken', response.refreshToken);
        this.localstorage.store('expiresAt', response.expiresAt);
        this.user.next(response);
      }),
      catchError((error: HttpErrorResponse) => {
        console.error('Token refresh failed:', error);
        this.logout();
        return throwError(() => new Error('Token refresh failed: ' + error.message));
      })
    );
  }

  getLoginStatus(): boolean {
    return this.loginStatus.value;
  }

  getHeaders(): HttpHeaders {
    return new HttpHeaders().set('Authorization', `Bearer ${this.getJwtToken()}`).set('Content-Type', 'application/json');
  }
}
