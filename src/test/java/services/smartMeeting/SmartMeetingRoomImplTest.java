package services.smartMeeting;

import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;

import static org.mockito.Mockito.*;

public class SmartMeetingRoomImplTest {

    @Mock
    private Connection mockConnection;

    @Mock
    private PreparedStatement mockPreparedStatement;

    @Mock
    private ResultSet mockResultSet;

    @Mock
    private StreamObserver<ActionResponse> actionResponseObserver;

    @Mock
    private StreamObserver<AvailabilityResponse> availabilityResponseObserver;

    @InjectMocks
    private SmartMeetingRoomImpl smartMeetingRoom;

    @BeforeEach
    public void setup() throws SQLException {
        MockitoAnnotations.openMocks(this);
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
    }

    @Test
    public void testBookRoomSuccess() throws SQLException {
        BookRoomRequest request = BookRoomRequest.newBuilder()
                .setRoomId(1)
                .setUserId(1)
                .setTimeSlot("10:00")
                .build();

        when(mockResultSet.next()).thenReturn(true);
        when(mockResultSet.getString("status")).thenReturn("AVAILABLE");
        when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);

        smartMeetingRoom.bookRoom(request, actionResponseObserver);

        verify(actionResponseObserver).onNext(any(ActionResponse.class));
        verify(actionResponseObserver).onCompleted();
    }

    @Test
    public void testBookRoomUnavailable() throws SQLException {
        BookRoomRequest request = BookRoomRequest.newBuilder()
                .setRoomId(4)
                .setUserId(101)
                .setTimeSlot("08:00")
                .build();

        when(mockResultSet.next()).thenReturn(true);
        when(mockResultSet.getString("status")).thenReturn("OCCUPIED");
        when(mockResultSet.getString("location")).thenReturn("floor 5");
        when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);

        smartMeetingRoom.bookRoom(request, actionResponseObserver);

        verify(actionResponseObserver).onNext(argThat(response -> !response.getSuccess()));
        verify(actionResponseObserver).onCompleted();
    }

    @Test
    public void testCancelBookingSuccess() throws SQLException {
        CancelBookingRequest request = CancelBookingRequest.newBuilder()
                .setBookingId(1)
                .build();

        when(mockPreparedStatement.executeUpdate()).thenReturn(1);

        smartMeetingRoom.cancelBooking(request, actionResponseObserver);

        verify(actionResponseObserver).onNext(any(ActionResponse.class));
        verify(actionResponseObserver).onCompleted();
    }

    @Test
    public void testCancelBookingFailure() throws SQLException {
        CancelBookingRequest request = CancelBookingRequest.newBuilder()
                .setBookingId(1)
                .build();

        when(mockPreparedStatement.executeUpdate()).thenReturn(0);

        smartMeetingRoom.cancelBooking(request, actionResponseObserver);

        verify(actionResponseObserver).onNext(argThat(response -> !response.getSuccess()));
        verify(actionResponseObserver).onCompleted();
    }

    @Test
    public void testCheckAvailabilitySuccess() throws SQLException {
        CheckAvailabilityRequest request = CheckAvailabilityRequest.newBuilder()
                .setRoomId(1)
                .build();

        when(mockResultSet.next()).thenReturn(true);
        when(mockResultSet.getString("status")).thenReturn("AVAILABLE");
        when(mockResultSet.getString("available_times")).thenReturn("[\"08:00\", \"13:00\", \"16:00\"]");
        when(mockResultSet.getString("location")).thenReturn("floor 5");
        when(mockResultSet.getInt("room_id")).thenReturn(1);
        when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);

        smartMeetingRoom.checkAvailability(request, availabilityResponseObserver);

        InOrder inOrder = inOrder(availabilityResponseObserver);
        inOrder.verify(availabilityResponseObserver).onNext(argThat(response ->
                response.getAvailableTimesList().containsAll(Arrays.asList("08:00", "13:00", "16:00")) &&
                        response.getDetails().getLocation().equals("floor 5")));
        inOrder.verify(availabilityResponseObserver).onCompleted();
    }


    @Test
    public void testCheckAvailabilityFailure() throws SQLException {
        CheckAvailabilityRequest request = CheckAvailabilityRequest.newBuilder()
                .setRoomId(1)
                .build();

        when(mockResultSet.next()).thenReturn(false);
        when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);

        smartMeetingRoom.checkAvailability(request, availabilityResponseObserver);

        verify(availabilityResponseObserver, never()).onNext(any(AvailabilityResponse.class));
        verify(availabilityResponseObserver).onCompleted();
    }
}
