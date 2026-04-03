package com.hacisimsek.inventoryservice.service;

import com.hacisimsek.inventory.dto.InventoryItemRequest;
import com.hacisimsek.inventory.model.InventoryItem;
import com.hacisimsek.inventory.repository.InventoryRepository;
import com.hacisimsek.inventory.service.impl.InventoryItemServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryItemServiceImplTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @InjectMocks
    private InventoryItemServiceImpl inventoryItemService;

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
                .reservedQuantity(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        itemRequest = new InventoryItemRequest("Test Product", "Test Description", 100);
    }

    @Test
    void createInventoryItem_ShouldCreateSuccessfully() {
        // Arrange
        when(inventoryRepository.save(any(InventoryItem.class))).thenReturn(inventoryItem);

        // Act
        InventoryItem result = inventoryItemService.createInventoryItem(itemRequest);

        // Assert
        assertNotNull(result);
        assertEquals("Test Product", result.getName());
        assertEquals("Test Description", result.getDescription());
        assertEquals(100, result.getAvailableQuantity());
        assertEquals(0, result.getReservedQuantity());
        verify(inventoryRepository, times(1)).save(any(InventoryItem.class));
    }

    @Test
    void getInventoryItemById_WhenExists_ShouldReturnItem() {
        // Arrange
        when(inventoryRepository.findById(itemId)).thenReturn(Optional.of(inventoryItem));

        // Act
        InventoryItem result = inventoryItemService.getInventoryItemById(itemId);

        // Assert
        assertNotNull(result);
        assertEquals(itemId, result.getId());
        assertEquals("Test Product", result.getName());
        verify(inventoryRepository, times(1)).findById(itemId);
    }

    @Test
    void getInventoryItemById_WhenNotExists_ShouldThrowException() {
        // Arrange
        when(inventoryRepository.findById(itemId)).thenReturn(Optional.empty());

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            inventoryItemService.getInventoryItemById(itemId);
        });

        assertTrue(exception.getMessage().contains("Inventory item not found"));
        verify(inventoryRepository, times(1)).findById(itemId);
    }

    @Test
    void getAllInventoryItems_ShouldReturnAllItems() {
        // Arrange
        InventoryItem item2 = InventoryItem.builder()
                .id(UUID.randomUUID())
                .name("Product 2")
                .availableQuantity(50)
                .reservedQuantity(0)
                .build();

        List<InventoryItem> items = Arrays.asList(inventoryItem, item2);
        when(inventoryRepository.findAll()).thenReturn(items);

        // Act
        List<InventoryItem> result = inventoryItemService.getAllInventoryItems();

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
        verify(inventoryRepository, times(1)).findAll();
    }

    @Test
    void updateInventoryItem_WhenExists_ShouldUpdateSuccessfully() {
        // Arrange
        InventoryItemRequest updateRequest = new InventoryItemRequest(
                "Updated Product", 
                "Updated Description", 
                150
        );
        
        when(inventoryRepository.findById(itemId)).thenReturn(Optional.of(inventoryItem));
        when(inventoryRepository.save(any(InventoryItem.class))).thenReturn(inventoryItem);

        // Act
        InventoryItem result = inventoryItemService.updateInventoryItem(itemId, updateRequest);

        // Assert
        assertNotNull(result);
        verify(inventoryRepository, times(1)).findById(itemId);
        verify(inventoryRepository, times(1)).save(argThat(item ->
            item.getName().equals("Updated Product") &&
            item.getDescription().equals("Updated Description") &&
            item.getAvailableQuantity() == 150
        ));
    }

    @Test
    void updateInventoryItem_WhenNotExists_ShouldThrowException() {
        // Arrange
        when(inventoryRepository.findById(itemId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(RuntimeException.class, () -> {
            inventoryItemService.updateInventoryItem(itemId, itemRequest);
        });

        verify(inventoryRepository, times(1)).findById(itemId);
        verify(inventoryRepository, never()).save(any());
    }

    @Test
    void deleteInventoryItem_WhenExists_ShouldDeleteSuccessfully() {
        // Arrange
        when(inventoryRepository.findById(itemId)).thenReturn(Optional.of(inventoryItem));
        doNothing().when(inventoryRepository).delete(any(InventoryItem.class));

        // Act
        inventoryItemService.deleteInventoryItem(itemId);

        // Assert
        verify(inventoryRepository, times(1)).findById(itemId);
        verify(inventoryRepository, times(1)).delete(inventoryItem);
    }

    @Test
    void deleteInventoryItem_WhenNotExists_ShouldThrowException() {
        // Arrange
        when(inventoryRepository.findById(itemId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(RuntimeException.class, () -> {
            inventoryItemService.deleteInventoryItem(itemId);
        });

        verify(inventoryRepository, times(1)).findById(itemId);
        verify(inventoryRepository, never()).delete(any());
    }

    @Test
    void checkAvailability_WhenSufficientStock_ShouldReturnTrue() {
        // Arrange
        when(inventoryRepository.findById(itemId)).thenReturn(Optional.of(inventoryItem));

        // Act
        boolean result = inventoryItemService.checkAvailability(itemId, 50);

        // Assert
        assertTrue(result);
        verify(inventoryRepository, times(1)).findById(itemId);
    }

    @Test
    void checkAvailability_WhenInsufficientStock_ShouldReturnFalse() {
        // Arrange
        when(inventoryRepository.findById(itemId)).thenReturn(Optional.of(inventoryItem));

        // Act
        boolean result = inventoryItemService.checkAvailability(itemId, 150);

        // Assert
        assertFalse(result);
        verify(inventoryRepository, times(1)).findById(itemId);
    }

    @Test
    void checkAvailability_WhenProductNotFound_ShouldReturnFalse() {
        // Arrange
        when(inventoryRepository.findById(itemId)).thenReturn(Optional.empty());

        // Act
        boolean result = inventoryItemService.checkAvailability(itemId, 50);

        // Assert
        assertFalse(result);
        verify(inventoryRepository, times(1)).findById(itemId);
    }

    @Test
    void checkAvailability_WhenExactQuantity_ShouldReturnTrue() {
        // Arrange
        when(inventoryRepository.findById(itemId)).thenReturn(Optional.of(inventoryItem));

        // Act
        boolean result = inventoryItemService.checkAvailability(itemId, 100);

        // Assert
        assertTrue(result);
        verify(inventoryRepository, times(1)).findById(itemId);
    }
}

// Made with Bob