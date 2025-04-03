import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { BehaviorSubject, Observable, catchError, map, of } from 'rxjs';
import { environment } from '../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private readonly TOKEN_KEY = 'auth_token';
  private readonly USER_KEY = 'current_user';
  private currentUserSubject = new BehaviorSubject<any>(this.getUserFromStorage());
  public currentUser$ = this.currentUserSubject.asObservable();

  constructor(private http: HttpClient) {}

  get currentUserValue() {
    return this.currentUserSubject.value;
  }

  login(username: string, password: string): Observable<any> {
      const options = {
        responseType: 'text' as 'json',
        headers: new HttpHeaders({
          'Accept': 'text/plain, application/json'
        })
      };

      return this.http.post(`${environment.apiUrl}/api/auth/login`, { username, password }, options)
        .pipe(
          map((token: any) => {
            // If the response is a string, use it directly
            // If it's an object with a token property, extract it
            const tokenValue = typeof token === 'string' ? token : (token as any)?.token || '';
            this.storeToken(tokenValue);
            this.storeUser({ username });
            this.currentUserSubject.next({ username });
            return tokenValue;
          }),
          catchError((error: any) => {
            if (error.status === 200 && error.statusText === 'OK') {
              const token = error.error?.text || '';
              if (token) {
                this.storeToken(token);
                this.storeUser({ username });
                this.currentUserSubject.next({ username });
                return of(token);
              }
            }
            throw error;
          })
        );
      }

  register(username: string, email: string, password: string): Observable<string> {
    return this.http.post(`${environment.apiUrl}/api/auth/register`, {
      username,
      email,
      password
    }, { responseType: 'text' });
  }

  logout(): void {
    localStorage.removeItem(this.TOKEN_KEY);
    localStorage.removeItem(this.USER_KEY);
    this.currentUserSubject.next(null);
  }

  isAuthenticated(): boolean {
    return !!this.getToken();
  }

  getToken(): string | null {
    return localStorage.getItem(this.TOKEN_KEY);
  }

  private storeToken(token: string): void {
    localStorage.setItem(this.TOKEN_KEY, token);
  }

  private storeUser(user: any): void {
    localStorage.setItem(this.USER_KEY, JSON.stringify(user));
  }

  private getUserFromStorage(): any {
    const user = localStorage.getItem(this.USER_KEY);
    return user ? JSON.parse(user) : null;
  }
}
