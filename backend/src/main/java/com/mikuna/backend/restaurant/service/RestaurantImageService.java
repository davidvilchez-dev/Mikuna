package com.mikuna.backend.restaurant.service;

import com.mikuna.backend.catalog.service.CloudinaryImageStorage;
import com.mikuna.backend.catalog.service.CloudinaryImageStorage.StoredImage;
import com.mikuna.backend.catalog.service.ImageValidator;
import com.mikuna.backend.common.exception.RegistrationValidationException;
import com.mikuna.backend.common.exception.RestaurantNotFoundException;
import com.mikuna.backend.restaurant.dto.RestaurantResponse;
import com.mikuna.backend.restaurant.model.Restaurant;
import com.mikuna.backend.restaurant.repository.RestaurantRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** Logo y portada del restaurante propio: JPG, PNG o WebP de hasta 2 MB, ambos opcionales. */
@Service
public class RestaurantImageService {

    private final RestaurantRepository restaurants;
    private final RestaurantService restaurantService;
    private final ImageValidator validator;
    private final CloudinaryImageStorage storage;

    public RestaurantImageService(RestaurantRepository restaurants, RestaurantService restaurantService,
            ImageValidator validator, CloudinaryImageStorage storage) {
        this.restaurants = restaurants;
        this.restaurantService = restaurantService;
        this.validator = validator;
        this.storage = storage;
    }

    @Transactional
    public RestaurantResponse update(UUID ownerId, MultipartFile logo, MultipartFile cover) {
        boolean hasLogo = logo != null && !logo.isEmpty();
        boolean hasCover = cover != null && !cover.isEmpty();
        if (!hasLogo && !hasCover) {
            throw new RegistrationValidationException("image", "Elige al menos una imagen");
        }
        Restaurant restaurant = restaurants.findByOwnerId(ownerId)
                .orElseThrow(() -> new RestaurantNotFoundException("Aún no tienes una solicitud de restaurante"));
        String oldLogo = restaurant.getLogoPublicId();
        String oldCover = restaurant.getCoverPublicId();
        byte[] logoBytes = hasLogo ? validator.validate(logo) : null;
        byte[] coverBytes = hasCover ? validator.validate(cover) : null;
        if (hasLogo) {
            StoredImage stored = storage.upload(logoBytes, "logo");
            restaurant.changeLogo(stored.secureUrl(), stored.publicId());
        }
        if (hasCover) {
            StoredImage stored = storage.upload(coverBytes, "portada");
            restaurant.changeCover(stored.secureUrl(), stored.publicId());
        }
        restaurants.saveAndFlush(restaurant);
        if (hasLogo && oldLogo != null) {
            storage.delete(oldLogo);
        }
        if (hasCover && oldCover != null) {
            storage.delete(oldCover);
        }
        return restaurantService.getMine(ownerId);
    }
}
