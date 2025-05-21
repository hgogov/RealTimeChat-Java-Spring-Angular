import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface RoomInvitation {
  id: number;
  roomId: number;
  roomName: string;
  invitedByUsername: string;
  status: string;
  createdAt: string;
}

@Injectable({
  providedIn: 'root'
})
export class RoomInvitationService {
  private apiUrl = `${environment.apiUrl}/api/invitations`;

  constructor(private http: HttpClient) {}

  getPendingInvitations(): Observable<RoomInvitation[]> {
    return this.http.get<RoomInvitation[]>(`${this.apiUrl}/pending`);
  }

  acceptInvitation(invitationId: number): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/${invitationId}/accept`, {});
  }

  declineInvitation(invitationId: number): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/${invitationId}/decline`, {});
  }
}
