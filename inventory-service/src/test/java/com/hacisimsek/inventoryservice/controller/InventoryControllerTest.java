package com.hacisimsek.inventoryservice.controller;

import com.hacisimsek.inventory.controller.InventoryController;
import com.hacisimsek.inventory.dto.InventoryItemRequest;
import com.hacisimsek.inventory.dto.InventoryItemResponse;
import com.hacisimsek.inventory.model.InventoryItem;
import com.hacisimsek.inventory.service.InventoryItemService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryControllerTest {

    @Mock
    private InventoryItemService inventoryItemService;

    @InjectMocks
    private InventoryController inventoryController;

    private UUID itemId;
    private InventoryItem inventoryItem;
    private InventoryItemRequest itemRequest;

    @BeforeEach
    void setUp() {
        itemId = UUID.randomUUID();
        
        inventoryItem = InventoryItem.builder()
                .id(itemId)
                .name("Test Product")
                .description("Test Description")
                .availableQuantity(100)
                .reservedQuantity(10)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        itemRequest = new InventoryItemRequest("Test Product", "Test Description", 100);
    }

    @Test
    void createInventoryItem_ShouldReturnCreatedStatus() {
        // Arrange
        when(inventoryItemService.createInventoryItem(any(InventoryItemRequest.class)))
                .thenReturn(inventoryItem);

        // Act
        ResponseEntity<InventoryItemResponse> response = inventoryController.createInventoryItem(itemRequest);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(itemId, response.getBody().getId());
        assertEquals("Test Product", response.getBody().getName());
        assertEquals(100, response.getBody().getAvailableQuantity());
        assertEquals(10, response.getBody().getReservedQuantity());
        verify(inventoryItemService, times(1)).createInventoryItem(any(InventoryItemRequest.class));
    }

    @Test
    void getInventoryItemById_ShouldReturnItem() {
        // Arrange
        when(inventoryItemService.getInventoryItemById(itemId)).thenReturn(inventoryItem);

        // Act
        ResponseEntity<InventoryItemResponse> response = inventoryController.getInventoryItemById(itemId);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(itemId, response.getBody().getId());
        assertEquals("Test Product", response.getBody().getName());
        verify(inventoryItemService, times(1)).getInventoryItemById(itemId);
    }

    @Test
    void getAllInventoryItems_ShouldReturnAllItems() {
        // Arrange
        InventoryItem item2 = InventoryItem.builder()
                .id(UUID.randomUUID())
                .name("Product 2")
                .description("Description 2")
                .availableQuantity(50)
                .reservedQuantity(5)
                .build();

        List<InventoryItem> items = Arrays.asList(inventoryItem, item2);
        when(inventoryItemService.getAllInventoryItems()).thenReturn(items);

        // Act
        ResponseEntity<List<InventoryItemResponse>> response = inventoryController.getAllInventoryItems();

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().size());
        assertEquals("Test Product", response.getBody().get(0).getName());
        assertEquals("Product 2", response.getBody().get(1).getName());
        verify(inventoryItemService, times(1)).getAllInventoryItems();
    }

    @Test
    void updateInventoryItem_ShouldReturnUpdatedItem() {
        // Arrange
        InventoryItemRequest updateRequest = new InventoryItemRequest(
                "Updated Product", 
                "Updated Description", 
                150
        );
        
        InventoryItem updatedItem = InventoryItem.builder()
                .id(itemId)
                .name("Updated Product")
                .description("Updated Description")
                .availableQuantity(150)
                .reservedQuantity(10)
                .build();

        when(inventoryItemService.updateInventoryItem(eq(itemId), any(InventoryItemRequest.class)))
                .thenReturn(updatedItem);

        // Act
        ResponseEntity<InventoryItemResponse> response = inventoryController.updateInventoryItem(itemId, updateRequest);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Updated Product", response.getBody().getName());
        assertEquals("Updated Description", response.getBody().getDescription());
        assertEquals(150, response.getBody().getAvailableQuantity());
        verify(inventoryItemService, times(1)).updateInventoryItem(eq(itemId), any(InventoryItemRequest.class));
    }

    @Test
    void deleteInventoryItem_ShouldReturnNoContent() {
        // Arrange
        doNothing().when(inventoryItemService).deleteInventoryItem(itemId);

        // Act
        ResponseEntity<Void> response = inventoryController.deleteInventoryItem(itemId);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertNull(response.getBody());
        verify(inventoryItemService, times(1)).deleteInventoryItem(itemId);
    }

    @Test
    void checkInventoryAvailability_WhenAvailable_ShouldReturnTrue() {
        // Arrange
        when(inventoryItemService.checkAvailability(itemId, 50)).thenReturn(true);

        // Act
        ResponseEntity<Boolean> response = inventoryController.checkInventoryAvailability(itemId, 50);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody());
        verify(inventoryItemService, times(1)).checkAvailability(itemId, 50);
    }

    @Test
    void checkInventoryAvailability_WhenNotAvailable_ShouldReturnFalse() {
        // Arrange
        when(inventoryItemService.checkAvailability(itemId, 200)).thenReturn(false);

        // Act
        ResponseEntity<Boolean> response = inventoryController.checkInventoryAvailability(itemId, 200);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody());
        verify(inventoryItemService, times(1)).checkAvailability(itemId, 200);
    }

    @Test
    void createInventoryItem_WithNullRequest_ShouldHandleGracefully() {
        // Arrange
        when(inventoryItemService.createInventoryItem(null))
                .thenThrow(new IllegalArgumentException("Request cannot be null"));

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            inventoryController.createInventoryItem(null);
        });
    }

    @Test
    void getInventoryItemById_WithNonExistentId_ShouldThrowException() {
        // Arrange
        UUID nonExistentId = UUID.randomUUID();
        when(inventoryItemService.getInventoryItemById(nonExistentId))
                .thenThrow(new RuntimeException("Inventory item not found"));

        // Act & Assert
        assertThrows(RuntimeException.class, () -> {
            inventoryController.getInventoryItemById(nonExistentId);
        });
        verify(inventoryItemService, times(1)).getInventoryItemById(nonExistentId);
    }
}

// Made with Bob