package com.mikuna.backend.catalog.service;

import com.mikuna.backend.common.cache.PublicCatalogChanged;
import com.mikuna.backend.catalog.dto.AvailabilityRequest;
import com.mikuna.backend.catalog.dto.PageResponse;
import com.mikuna.backend.catalog.dto.ProductRequest;
import com.mikuna.backend.catalog.dto.ProductResponse;
import com.mikuna.backend.catalog.model.Product;
import com.mikuna.backend.catalog.repository.ProductRepository;
import com.mikuna.backend.catalog.service.CloudinaryImageStorage.StoredImage;
import com.mikuna.backend.common.exception.ProductConflictException;
import com.mikuna.backend.common.exception.ProductNotFoundException;
import com.mikuna.backend.common.exception.RegistrationValidationException;
import com.mikuna.backend.common.exception.RestaurantNotFoundException;
import com.mikuna.backend.common.exception.RestaurantStateException;
import com.mikuna.backend.restaurant.model.Restaurant;
import com.mikuna.backend.restaurant.model.RestaurantStatus;
import com.mikuna.backend.restaurant.repository.RestaurantRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

/**
 * Gestión del catálogo propio (RF-08). El restaurante siempre se resuelve desde la cuenta del token y los
 * productos se buscan por su id y ese restaurante: uno ajeno es indistinguible de uno inexistente (RF-04).
 */
@Service
public class ProductService {

    private static final BigDecimal MIN_PRICE = new BigDecimal("0.01");
    private static final BigDecimal MAX_PRICE = new BigDecimal("9999.99");
    private static final int MAX_COPY = 500;

    private final ProductRepository products;
    private final RestaurantRepository restaurants;
    private final ImageValidator imageValidator;
    private final CloudinaryImageStorage storage;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    public ProductService(ProductRepository products, RestaurantRepository restaurants,
            ImageValidator imageValidator, CloudinaryImageStorage storage, Clock clock,
            ApplicationEventPublisher events) {
        this.events = events;
        this.products = products;
        this.restaurants = restaurants;
        this.imageValidator = imageValidator;
        this.storage = storage;
        this.clock = clock;
    }

    @Transactional
    public ProductResponse create(UUID ownerId, ProductRequest request, MultipartFile image) {
        ValidatedProduct data = validate(request);
        if (request.version() != null) {
            throw new RegistrationValidationException("version", "No envíes la versión al crear un producto");
        }
        Restaurant restaurant = approvedRestaurant(ownerId);
        byte[] content = imageValidator.validate(image);

        StoredImage stored = storage.upload(content, filename(image));
        try {
            Product saved = products.saveAndFlush(Product.create(restaurant.getId(), data.name(), data.description(),
                    data.category(), data.price(), stored.secureUrl(), stored.publicId(), Instant.now(clock)));
            events.publishEvent(PublicCatalogChanged.catalog(restaurant.getId()));
            return ProductResponse.from(saved);
        } catch (DataIntegrityViolationException duplicate) {
            storage.delete(stored.publicId());
            throw new ProductConflictException(ProductConflictException.DUPLICATE);
        } catch (RuntimeException failure) {
            storage.delete(stored.publicId());
            throw failure;
        }
    }

    /**
     * Copia a la sucursal actual los productos activos de otra sucursal de la misma cuenta. Cada imagen se vuelve a
     * subir (no se comparte), así cambiarla o reemplazarla en una sucursal no afecta a las demás. Los productos que
     * ya existan con el mismo nombre y categoría se omiten.
     */
    @Transactional
    public CopyMenuResult copyMenu(UUID ownerId, UUID sourceRestaurantId) {
        Restaurant target = approvedRestaurant(ownerId);
        if (target.getId().equals(sourceRestaurantId)) {
            throw new RegistrationValidationException("source", "Elige otra sucursal para copiar su carta");
        }
        Restaurant source = restaurants.findByIdAndOwnerId(sourceRestaurantId, ownerId)
                .orElseThrow(() -> new RestaurantNotFoundException("Sucursal no encontrada"));

        java.util.Set<String> existing = new java.util.HashSet<>();
        products.findByRestaurantIdAndActiveTrue(target.getId(), PageRequest.of(0, MAX_COPY)).forEach(
                product -> existing.add(Product.key(product.getName()) + "|" + Product.key(product.getCategory())));

        java.util.List<String> uploaded = new java.util.ArrayList<>();
        int copied = 0;
        int skipped = 0;
        try {
            for (Product original : products.findByRestaurantIdAndActiveTrue(source.getId(),
                    PageRequest.of(0, MAX_COPY, Sort.by("category", "name"))).getContent()) {
                if (!existing.add(Product.key(original.getName()) + "|" + Product.key(original.getCategory()))) {
                    skipped++;
                    continue;
                }
                StoredImage image = storage.copyFrom(original.getImageUrl());
                uploaded.add(image.publicId());
                Product copy = Product.create(target.getId(), original.getName(), original.getDescription(),
                        original.getCategory(), original.getPrice(), image.secureUrl(), image.publicId(),
                        Instant.now(clock));
                copy.setAvailable(original.isAvailable());
                products.save(copy);
                copied++;
            }
            products.flush();
        } catch (RuntimeException failure) {
            uploaded.forEach(storage::delete);
            throw failure;
        }
        events.publishEvent(PublicCatalogChanged.catalog(target.getId()));
        return new CopyMenuResult(copied, skipped);
    }

    public record CopyMenuResult(int copied, int skipped) {
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> list(UUID ownerId, int page, int size) {
        Paging.validate(page, size);
        Restaurant restaurant = approvedRestaurant(ownerId);
        return PageResponse.from(products.findByRestaurantIdAndActiveTrue(restaurant.getId(),
                PageRequest.of(page, size, Sort.by("category", "name"))), ProductResponse::from);
    }

    @Transactional(readOnly = true)
    public ProductResponse get(UUID ownerId, UUID productId) {
        return ProductResponse.from(own(approvedRestaurant(ownerId), productId));
    }

    /** Edita los datos; si llega una imagen nueva sustituye la anterior, si no se conserva la actual. */
    @Transactional
    public ProductResponse update(UUID ownerId, UUID productId, ProductRequest request, MultipartFile image) {
        ValidatedProduct data = validate(request);
        Restaurant restaurant = approvedRestaurant(ownerId);
        Product product = own(restaurant, productId);
        requireVersion(request.version(), product);

        String previousImage = null;
        StoredImage stored = null;
        if (image != null && !image.isEmpty()) {
            byte[] content = imageValidator.validate(image);
            stored = storage.upload(content, filename(image));
            previousImage = product.getImagePublicId();
            product.changeImage(stored.secureUrl(), stored.publicId());
        }
        product.update(data.name(), data.description(), data.category(), data.price());
        try {
            Product saved = products.saveAndFlush(product);
            events.publishEvent(PublicCatalogChanged.catalog(restaurant.getId()));
            if (previousImage != null) {
                String toDelete = previousImage;
                afterCommit(() -> storage.delete(toDelete));
            }
            return ProductResponse.from(saved);
        } catch (DataIntegrityViolationException duplicate) {
            discard(stored);
            throw new ProductConflictException(ProductConflictException.DUPLICATE);
        } catch (ObjectOptimisticLockingFailureException stale) {
            discard(stored);
            throw new ProductConflictException(ProductConflictException.STALE);
        } catch (RuntimeException failure) {
            discard(stored);
            throw failure;
        }
    }

    @Transactional
    public ProductResponse setAvailability(UUID ownerId, UUID productId, AvailabilityRequest request) {
        if (request == null || request.available() == null) {
            throw new RegistrationValidationException("available", "Indica si el producto está disponible");
        }
        Product product = own(approvedRestaurant(ownerId), productId);
        requireVersion(request.version(), product);
        product.setAvailable(request.available());
        ProductResponse saved = ProductResponse.from(saveStrict(product));
        events.publishEvent(PublicCatalogChanged.catalog(product.getRestaurantId()));
        return saved;
    }

    /** Retiro lógico y definitivo; no borra la imagen (la referencia histórica de pedidos la necesitará). */
    @Transactional
    public void withdraw(UUID ownerId, UUID productId, Long version) {
        Product product = own(approvedRestaurant(ownerId), productId);
        requireVersion(version, product);
        product.withdraw(Instant.now(clock));
        saveStrict(product);
        events.publishEvent(PublicCatalogChanged.catalog(product.getRestaurantId()));
    }

    private Restaurant approvedRestaurant(UUID ownerId) {
        Restaurant restaurant = restaurants.findByOwnerId(ownerId)
                .orElseThrow(() -> new RestaurantNotFoundException("Aún no tienes una solicitud de restaurante"));
        if (restaurant.getStatus() != RestaurantStatus.APPROVED) {
            throw new RestaurantStateException("Tu restaurante debe estar aprobado para gestionar productos");
        }
        return restaurant;
    }

    private Product own(Restaurant restaurant, UUID productId) {
        return products.findByIdAndRestaurantIdAndActiveTrue(productId, restaurant.getId())
                .orElseThrow(ProductNotFoundException::new);
    }

    private static void requireVersion(Long expected, Product product) {
        if (expected == null) {
            throw new RegistrationValidationException("version", "La versión es obligatoria");
        }
        if (expected != product.getVersion()) {
            throw new ProductConflictException(ProductConflictException.STALE);
        }
    }

    private Product saveStrict(Product product) {
        try {
            return products.saveAndFlush(product);
        } catch (ObjectOptimisticLockingFailureException stale) {
            throw new ProductConflictException(ProductConflictException.STALE);
        }
    }

    private void discard(StoredImage stored) {
        if (stored != null) {
            storage.delete(stored.publicId());
        }
    }

    /** Ejecuta la acción tras confirmar la transacción; sin transacción activa, de inmediato. */
    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    private static String filename(MultipartFile image) {
        String name = image.getOriginalFilename();
        return name == null || name.isBlank() ? "producto" : name;
    }

    private record ValidatedProduct(String name, String description, String category, BigDecimal price) {
    }

    /** Revalida en el servicio las reglas de data-model.md (constitución II). */
    private static ValidatedProduct validate(ProductRequest request) {
        if (request == null) {
            throw new RegistrationValidationException("name", "Completa los datos del producto");
        }
        String name = required("name", request.name(), 2, 100, "El nombre admite de 2 a 100 caracteres");
        String category = required("category", request.category(), 2, 50, "La categoría admite de 2 a 50 caracteres");
        String description = request.description() == null ? "" : request.description().trim();
        if (description.length() > 300) {
            throw new RegistrationValidationException("description", "La descripción admite hasta 300 caracteres");
        }
        BigDecimal price = request.price();
        if (price == null) {
            throw new RegistrationValidationException("price", "El precio es obligatorio");
        }
        if (price.stripTrailingZeros().scale() > 2) {
            throw new RegistrationValidationException("price", "El precio admite hasta 2 decimales");
        }
        if (price.compareTo(MIN_PRICE) < 0) {
            throw new RegistrationValidationException("price", "El precio debe ser mayor que cero");
        }
        if (price.compareTo(MAX_PRICE) > 0) {
            throw new RegistrationValidationException("price", "El precio máximo es 9999.99");
        }
        return new ValidatedProduct(name, description.isEmpty() ? null : description, category,
                price.setScale(2));
    }

    private static String required(String field, String value, int min, int max, String message) {
        String clean = value == null ? "" : value.trim();
        if (clean.length() < min || clean.length() > max) {
            throw new RegistrationValidationException(field, message);
        }
        return clean;
    }
}
