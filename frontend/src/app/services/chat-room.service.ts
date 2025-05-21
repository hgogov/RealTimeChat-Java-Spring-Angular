import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface ChatRoom {
    id: number;
    name: string;
    createdByUsername?: string;
    createdAt: string;
    isPublic: boolean;
}

export interface CreateRoomPayload {
    name: string;
    isPublic: boolean;
}

export interface InviteUserPayload {
  username: string;
}

@Injectable({
  providedIn: 'root'
})
export class ChatRoomService {
  private apiUrl = `${environment.apiUrl}/api/rooms`;

  constructor(private http: HttpClient) {}

  getUserRooms(): Observable<ChatRoom[]> {
    return this.http.get<ChatRoom[]>(this.apiUrl);
  }

  createRoom(payload: CreateRoomPayload): Observable<ChatRoom> {
    console.log('Service sending payload:', payload);
    return this.http.post<ChatRoom>(this.apiUrl, payload);
  }

  joinRoom(roomId: number): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/${roomId}/join`, {});
  }

  leaveRoom(roomId: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${roomId}/leave`);
  }

  inviteUser(roomId: number, payload: InviteUserPayload): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/${roomId}/invites`, payload);
  }

  getDiscoverableRooms(): Observable<ChatRoom[]> {
    return this.http.get<ChatRoom[]>(`${this.apiUrl}/discoverable`);
  }
}
