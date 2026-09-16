# System Overview Reference

This document provides a high-level overview of the Word Online game server architecture, technology stack, and networking model.

## Technology Stack

- **Language**: Java 21 (Gradle-based build)
- **Framework**: Spring Boot 3.x
- **Data Access**: Spring Data JPA / Hibernate
- **Database**: PostgreSQL (Production), H2 (Local / Test)
- **Messaging**: STOMP over WebSockets (Spring WebSocket Message Broker)
- **Testing**: JUnit Platform, Spring Security Test, Awaitility, H2

## Network Topology

The game operates on an authoritative server model. The client sends player inputs (cast requests, pings) to the server, and the server simulates the game logic, physics, and state updates, then sends frame/snapshot updates back to the clients.

### Data Communication Flow

1. **Client Action (Input)**: The client sends cast inputs or ping requests as STOMP JSON messages over a WebSocket connection to the server endpoint `/app/game/input/{sessionId}/{userId}`.
2. **Server Routing (Controller)**: [InputController](file:///Users/jeong-yunseong/development/word-online/dev/game-server/src/main/java/com/wordonline/server/game/controller/InputController.java) receives the payload, validates user credentials, and forwards the command to the session's `MagicInputHandler`.
3. **Simulation Tick (Game Loop)**: The thread running `GameLoop` updates the spatial positions, resolves physics collisions, and mutates GameObject elements.
4. **State Broadcasting (Frame Update)**: At the end of every tick, the game server compiles frame events and incremental updates (mana, card drawing, and position changes). It publishes these payloads to:
   - `/topic/game/{sessionId}/frameInfos/{userId}` (for individual player views).
   - `/topic/game/{sessionId}/frameInfos` (for broadcast views/spectators).

### Cast Protocol

A card is one magic. Every input message names the card by its `magics.id`, and there is no card
list, no combination to assemble, and therefore no `toggleCard` or `cancelCard`.

```json
{ "type": "useMagic",     "magicId": 34, "id": 7, "position": { "x": 9.0, "y": 0.0, "z": 5.0 } }
{ "type": "selectCard",   "magicId": 34, "id": 7 }
{ "type": "unselectCard", "magicId": 34, "id": 7 }
{ "type": "ping" }
```

- `id` is a request number the client assigns; `InputResponseDto` returns it unchanged.
- `selectCard` and `unselectCard` put an idle aura of the magic's element on the caster and take it
  off again, so the opponent can see a cast coming. The element comes from `magics.element`; a magic
  whose element is `None` gets no aura.
- The frame update's `cards.added` is a list of `magics.id`, not card name strings.

### Protocol Specifications
- **WebSocket Connection**: Handled via [WebSocketConfig.java](file:///Users/jeong-yunseong/development/word-online/dev/game-server/src/main/java/com/wordonline/server/websocket/WebSocketConfig.java). Securing channels is managed by [JwtChannelInterceptor.java](file:///Users/jeong-yunseong/development/word-online/dev/game-server/src/main/java/com/wordonline/server/websocket/JwtChannelInterceptor.java) and [JwtHandshakeInterceptor.java](file:///Users/jeong-yunseong/development/word-online/dev/game-server/src/main/java/com/wordonline/server/websocket/JwtHandshakeInterceptor.java).
- **Client Input Target**: `/game/input/{sessionId}/{userId}`
- **Server Broadcast Target**: `/game/{sessionId}/frameInfos/{userId}` (for player-specific updates) and `/game/{sessionId}/frameInfos` (for spectator broadcasts).
