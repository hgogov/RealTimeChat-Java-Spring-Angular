import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface ChatRoom {
    id: number;
    name: string;
    createdByUsername?: string;
    createdAt: string;
}

export interface CreateRoomPayload {
    name: string;
}

@Injectable({
  providedIn: 'root'
})
export class ChatRoomService {
  private apiUrl = `${environment.apiUrl}/api/rooms`;

  constructor(private http: HttpClient) {}

  // Get rooms for the current logged-in user
  getUserRooms(): Observable<ChatRoom[]> {
    return this.http.get<ChatRoom[]>(this.apiUrl);
  }

  // Create a new chat room
  createRoom(payload: CreateRoomPayload): Observable<ChatRoom> {
    return this.http.post<ChatRoom>(this.apiUrl, payload);
  }

  // TODO: Add methods for joining, leaving, getting details by ID etc.
}
