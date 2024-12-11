import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

const connectWebSocket = (onMessage) => {
    // URL of the WebSocket endpoint
    const socketUrl = "http://localhost:8080/alerts-websocket";

    // Initialize the STOMP Client
    const stompClient = new Client({
        webSocketFactory: () => new SockJS(socketUrl), // Use SockJS for WebSocket fallback
        debug: (str) => console.log(str), // Optional: Log WebSocket events to console
        reconnectDelay: 5000, // Reconnect after a 5-second delay if disconnected
    });

    // When connected, subscribe to the topic
    stompClient.onConnect = () => {
        console.log("Connected to WebSocket");
        stompClient.subscribe('/topic/alerts', (message) => {
            console.log("Received message:", message.body);
            onMessage(message.body); // Directly use the message body if it's plain text
        });
    };

    // Handle disconnection and errors (optional)
    stompClient.onStompError = (frame) => {
        console.error("WebSocket error:", frame.headers['message']);
        console.error("Details:", frame.body);
    };

    // Activate the client (open the connection)
    stompClient.activate();

    return stompClient; // Return the client instance to allow disconnection later
};

export default connectWebSocket;