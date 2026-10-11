package com.trainbooking.it25101520;

import com.trainbooking.it25101520.dto.*;
import com.trainbooking.it25101520.model.*;
import com.trainbooking.it25101520.repository.*;
import com.trainbooking.it25101520.service.PassengerService;
import com.trainbooking.it25102327.model.Route;
import com.trainbooking.it25102327.repository.RouteRepository;
import com.trainbooking.exception.ResourceNotFoundException;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class FavoriteCrudTest {
    UserRepository users; FavoriteRouteRepository favorites; RouteRepository routes; PassengerService service; User own;
    @BeforeEach void setup() {
        users=mock(UserRepository.class); favorites=mock(FavoriteRouteRepository.class); routes=mock(RouteRepository.class);
        service=new PassengerService(users,favorites,routes); own=User.builder().id(7L).build();
        when(users.findByIdForUpdate(7L)).thenReturn(Optional.of(own));
        when(routes.findAll()).thenReturn(List.of(Route.builder().origin("Colombo Fort").destination("Kandy").build(),
            Route.builder().origin("Colombo Fort").destination("Galle").build()));
        when(favorites.save(any())).thenAnswer(i->i.getArgument(0));
    }
    FavoriteRouteDto pair(String origin,String destination) { return FavoriteRouteDto.builder().origin(origin).destination(destination).build(); }
    @Test void canonicalStationNamesAreSavedRatherThanTypedAliases() {
        var result=service.addFavoriteRoute(7L,pair(" colombo fort ","kandy"));
        assertEquals("Colombo Fort",result.getOrigin()); assertEquals("Kandy",result.getDestination()); assertEquals(own,result.getUser());
    }
    @Test void duplicateIsRejectedWithoutSaving() {
        when(favorites.existsByUserIdAndOriginIgnoreCaseAndDestinationIgnoreCase(7L,"Colombo Fort","Kandy")).thenReturn(true);
        assertThrows(IllegalArgumentException.class,()->service.addFavoriteRoute(7L,pair("Colombo Fort","Kandy")));
        verify(favorites,never()).save(any());
    }
    @Test void invalidOrUnconfiguredPairCannotBeSaved() {
        for(var pair:List.of(pair("","Kandy"),pair("Kandy","Kandy"),pair("Kandy","Galle"),pair("unknown","Galle"))) {
            assertThrows(IllegalArgumentException.class,()->service.addFavoriteRoute(7L,pair));
        }
        verify(favorites,never()).save(any());
    }
    @Test void editUsesOwnerAndPreservesIdentity() {
        var existing=FavoriteRoute.builder().id(9L).user(own).origin("Colombo Fort").destination("Kandy").build();
        when(favorites.findByIdAndUserId(9L,7L)).thenReturn(Optional.of(existing));
        var changed=service.updateFavoriteRoute(9L,7L,pair("Colombo Fort","Galle"));
        assertEquals(9L,changed.getId()); assertEquals("Galle",changed.getDestination()); assertEquals(own,changed.getUser());
    }
    @Test void duplicateEditDoesNotMutateTheOriginal() {
        var existing=FavoriteRoute.builder().id(9L).user(own).origin("Colombo Fort").destination("Kandy").build();
        when(favorites.findByIdAndUserId(9L,7L)).thenReturn(Optional.of(existing));
        when(favorites.existsByUserIdAndOriginIgnoreCaseAndDestinationIgnoreCaseAndIdNot(7L,"Colombo Fort","Galle",9L)).thenReturn(true);
        assertThrows(IllegalArgumentException.class,()->service.updateFavoriteRoute(9L,7L,pair("Colombo Fort","Galle")));
        assertEquals("Kandy",existing.getDestination()); verify(favorites,never()).save(any());
    }
    @Test void foreignFavoriteCannotBeReadEditedOrRemoved() {
        assertThrows(ResourceNotFoundException.class,()->service.getFavoriteRoute(9L,7L));
        assertThrows(ResourceNotFoundException.class,()->service.updateFavoriteRoute(9L,7L,pair("Colombo Fort","Galle")));
        assertThrows(ResourceNotFoundException.class,()->service.removeFavoriteRoute(9L,7L));
        verify(favorites,never()).delete(any(FavoriteRoute.class)); verify(favorites,never()).save(any());
    }
    @Test void ownFavoriteIsDeleted() {
        var existing=FavoriteRoute.builder().id(9L).user(own).build();
        when(favorites.findByIdAndUserId(9L,7L)).thenReturn(Optional.of(existing));
        service.removeFavoriteRoute(9L,7L); verify(favorites).delete(existing);
    }
    @Test void invalidProfileCannotMutateStoredFields() {
        var profile=UserProfileDto.builder().firstName("Test").lastName("Passenger").phone("0771234567").delayAlertThresholdMinutes(-1).build();
        assertThrows(IllegalArgumentException.class,()->service.updateProfile(7L,profile)); verify(users,never()).save(any());
    }
}
