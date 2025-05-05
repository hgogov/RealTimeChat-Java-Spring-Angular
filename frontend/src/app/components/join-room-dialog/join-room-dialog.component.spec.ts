import { ComponentFixture, TestBed } from '@angular/core/testing';

import { JoinRoomDialogComponent } from './join-room-dialog.component';

describe('JoinRoomDialogComponent', () => {
  let component: JoinRoomDialogComponent;
  let fixture: ComponentFixture<JoinRoomDialogComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [JoinRoomDialogComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(JoinRoomDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
