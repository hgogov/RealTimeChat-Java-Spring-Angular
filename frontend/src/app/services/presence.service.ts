import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class PresenceService {
  private apiUrlBase = `${environment.apiUrl}/api/rooms`;

  constructor(private http: HttpClient) {}

  getOnlineMembers(roomId: number): Observable<string[]> {
    return this.http.get<string[]>(`${this.apiUrlBase}/${roomId}/presence`);
  }
}
