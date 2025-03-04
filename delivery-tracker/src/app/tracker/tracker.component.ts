import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { DeliveryService } from '../services/delivery.service';
import { UserService } from '../services/user.service';
import * as L from 'leaflet';
import { Subscription } from 'rxjs';

@Component({
  selector: 'app-tracker',
  templateUrl: './tracker.component.html',
  styleUrls: ['./tracker.component.css'],
})
export class TrackerComponent implements OnInit, OnDestroy {
  currentOrderId: string = '';
  currentOrder: any;
  map: any;
  vehicleMarker: any;
  locationSubscription: Subscription = new Subscription();

  constructor(
    private route: ActivatedRoute,
    private deliveryService: DeliveryService,
    private userService: UserService
  ) {}

  ngOnInit(): void {
    this.route.queryParams.subscribe((params) => {
      this.currentOrderId = params['orderId'];
      if (this.currentOrderId) {
        this.loadOrderDetails();
      }
    });
  }

  // Load Order Details and Initialize Map
  private loadOrderDetails(): void {
    this.userService.getOrderDetails(this.currentOrderId).subscribe({
      next: (order) => {
        this.currentOrder = order;

        if (order.lat && order.lon && order.desLat && order.desLon) {
          this.initMap(order.lat, order.lon, order.desLat, order.desLon);
          this.trackLiveLocation();
        } else {
          console.error('Invalid order coordinates');
        }
      },
      error: (err) => {
        console.error('Error fetching order details:', err);
      },
    });
  }

  // Initialize Leaflet Map
  private initMap(
    lat: number,
    lon: number,
    desLat: number,
    desLon: number
  ): void {
    // Avoid map reinitialization
    if (this.map) {
      this.map.remove();
    }

    const mapId = `map-tracker-${this.currentOrderId}`;
    setTimeout(() => {
      this.map = L.map(mapId).setView([lat, lon], 13);

      L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        attribution: '&copy; OpenStreetMap contributors',
      }).addTo(this.map);

      // Source Marker
      L.marker([lat, lon], { icon: this.createCustomIcon('Source') })
        .addTo(this.map)
        .bindPopup('Source Location')
        .openPopup();

      // Destination Marker
      L.marker([desLat, desLon], { icon: this.createCustomIcon('Destination') })
        .addTo(this.map)
        .bindPopup('Destination Location');

      // Draw Polyline (Path between Source & Destination)
      L.polyline([[lat, lon], [desLat, desLon]], { color: 'blue' }).addTo(
        this.map
      );
    });
  }

  // Track Live Vehicle Location
  private trackLiveLocation(): void {
    this.locationSubscription = this.deliveryService
      .getLiveLocation(this.currentOrderId)
      .subscribe({
        next: (position) => {
          if (position.lat && position.lon) {
            if (this.vehicleMarker) {
              this.vehicleMarker.setLatLng([position.lat, position.lon]);
            } else {
              this.vehicleMarker = L.marker(
                [position.lat, position.lon],
                { icon: this.createVehicleIcon() }
              ).addTo(this.map);
            }
            // Keep the map centered on the vehicle
            this.map.setView([position.lat, position.lon], this.map.getZoom());
          }
        },
        error: (err) => {
          console.error('Error fetching live location:', err);
        },
      });
  }

  // Create Custom Icon for Source & Destination
  private createCustomIcon(label: string): L.DivIcon {
    return L.divIcon({
      className: 'custom-marker',
      html: `<div class="marker-label">${label}</div>`,
      iconSize: [30, 42],
      iconAnchor: [15, 42],
    });
  }

  // Create Vehicle Icon for Live Tracking
  private createVehicleIcon(): L.DivIcon {
    return L.divIcon({
      className: 'vehicle-marker',
      html: '<div class="vehicle-icon">🚚</div>',
      iconSize: [30, 42],
      iconAnchor: [15, 42],
    });
  }

  ngOnDestroy(): void {
    this.locationSubscription.unsubscribe();
    if (this.map) {
      this.map.remove();
    }
  }
}
