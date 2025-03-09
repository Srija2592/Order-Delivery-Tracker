import { Component, AfterViewInit, OnInit, OnDestroy, ChangeDetectorRef } from '@angular/core';
import * as L from 'leaflet';
import { DeliveryService, LocationUpdate } from '../services/delivery.service';
import { UserService } from '../services/user.service';
import { lastValueFrom, Subscription } from 'rxjs';
import { ActivatedRoute } from '@angular/router';

@Component({
  selector: 'app-tracker',
  templateUrl: './tracker.component.html',
  styleUrls: ['./tracker.component.css'],
})
export class TrackerComponent implements OnInit, AfterViewInit, OnDestroy {
  currentOrderId: string = '';
  currentOrder: {
    srcLat?: number;
    srcLon?: number;
    desLat?: number;
    desLon?: number;
    status?: string;
    curLat: number;
    curLon: number;
  } | null = null;

  private map: L.Map | undefined;
  private vehicleMarker: L.Marker | undefined;
  private locationSubscription: Subscription = new Subscription();
  private dynamicPolyline: L.Polyline | null = null; // New dynamic polyline
  private pathCoordinates: L.LatLng[] = []; // Store dynamic path points

  constructor(
    private route: ActivatedRoute,
    private deliveryService: DeliveryService,
    private userService: UserService,
    private cdr: ChangeDetectorRef // For change detection
  ) {}

  ngOnInit(): void {
    this.route.queryParams.subscribe(async (params) => {
      this.currentOrderId = params['orderId'] || '';
      if (this.currentOrderId) {
        await this.loadOrderDetails();
        this.trackLiveLocation(); // Start tracking after loading
      } else {
        console.warn('No orderId provided');
      }
    });
  }

  private async loadOrderDetails(): Promise<void> {
    try {
      const order = await lastValueFrom(this.userService.getOrderDetails(this.currentOrderId));
      this.currentOrder = {
        srcLat: order.srcLat,
        srcLon: order.srcLon,
        desLat: order.desLat,
        desLon: order.desLon,
        status: order.status,
        curLat: order.curLat || order.srcLat,
        curLon: order.curLon || order.srcLon,
      };
      // Fetch the latest location
      const liveLocation = await lastValueFrom(this.userService.getLiveLocation(this.currentOrderId));
      if (liveLocation.curLat && liveLocation.curLon) {
        this.currentOrder.curLat = liveLocation.curLat;
        this.currentOrder.curLon = liveLocation.curLon;
      }
      if (this.currentOrder.srcLat && this.currentOrder.srcLon && this.currentOrder.desLat && this.currentOrder.desLon) {
        this.initMap();
      } else {
        console.error('Invalid order coordinates');
      }
    } catch (err) {
      console.error('Error fetching order details:', err);
      this.currentOrder = null;
    }
  }

  private initMap(): void {
    if (!this.map && this.currentOrder) {
      const mapId = `map-tracker-${this.currentOrderId}`;
      const mapElement = document.getElementById(mapId);

      if (!mapElement) {
        console.error('Map element not found');
        return;
      }

      this.map = L.map(mapId).setView([this.currentOrder!.srcLat!, this.currentOrder!.srcLon!], 6);

      L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        attribution: '© OpenStreetMap contributors',
      }).addTo(this.map);

      // Source Marker
      L.marker([this.currentOrder!.srcLat!, this.currentOrder!.srcLon!], { icon: this.createCustomIcon('Source') })
        .addTo(this.map).bindPopup('Source: Hyderabad').openPopup();

      // Destination Marker
      L.marker([this.currentOrder!.desLat!, this.currentOrder!.desLon!], { icon: this.createCustomIcon('Destination') })
        .addTo(this.map).bindPopup('Destination: Hyderabad');

        L.marker([this.currentOrder!.curLat!, this.currentOrder!.curLon!], { icon: this.createCustomIcon('Current') })
        .addTo(this.map).bindPopup('Destination: Hyderabad');

      // // Static Blue Polyline (Source to Destination)
      // L.polyline(
      //   [[this.currentOrder!.srcLat!, this.currentOrder!.srcLon!], [this.currentOrder!.desLat!, this.currentOrder!.desLon!]],
      //   { color: 'blue' }
      // ).addTo(this.map);

      // Initial Vehicle Marker (start at current location)
      this.vehicleMarker = L.marker([this.currentOrder!.curLat!, this.currentOrder!.curLon!], {
        icon: this.createVehicleIcon(),
      }).addTo(this.map).bindPopup('Vehicle Location');

      // Initialize dynamic path with starting point
      this.pathCoordinates.push(L.latLng(this.currentOrder!.curLat!, this.currentOrder!.curLon!));
      this.dynamicPolyline = L.polyline(this.pathCoordinates, { color: 'blue' }).addTo(this.map);

      this.map?.invalidateSize();
      this.cdr.detectChanges();
    }
  }

  private trackLiveLocation(): void {
    this.locationSubscription = this.deliveryService.getLocationUpdates(this.currentOrderId).subscribe({
      next: (update: LocationUpdate) => {
        console.log('Received Update:', update);
        if (update.vehicleId === this.currentOrderId && update.curLat && update.curLon) {
          if (this.vehicleMarker) {
            this.vehicleMarker.setLatLng([update.curLat, update.curLon]);
          } else {
            this.vehicleMarker = L.marker([update.curLat, update.curLon], {
              icon: this.createVehicleIcon(),
            }).addTo(this.map!).bindPopup('Vehicle Location');
          }
          this.currentOrder!.curLat = update.curLat;
          this.currentOrder!.curLon = update.curLon;
          this.map?.panTo([update.curLat, update.curLon]);
          console.log('Marker updated to:', [update.curLat, update.curLon]);

          // Update dynamic polyline with current path
          this.pathCoordinates.push(L.latLng(update.curLat, update.curLon));
          if (this.dynamicPolyline) {
            this.dynamicPolyline.setLatLngs(this.pathCoordinates);
            this.dynamicPolyline.bringToFront(); // Ensure dynamic polyline is visible
          } else {
            this.dynamicPolyline = L.polyline(this.pathCoordinates, { color: 'red' }).addTo(this.map!);
          }
          this.cdr.detectChanges();
        } else {
          console.warn('Update ignored, vehicleId mismatch or invalid coordinates:', update);
        }
      },
      error: (err) => console.error('WebSocket error:', err),
    });
  }

  ngAfterViewInit(): void {
    if (this.currentOrderId) {
      this.initMap();
      this.trackLiveLocation();
    }
  }

  private createCustomIcon(type: 'Source' | 'Destination' |'Current'): L.Icon {
    return L.icon({
      iconUrl: type === 'Source' ? 'assets/images/red-marker.jpeg' :
      type === 'Destination' ? 'assets/images/green-marker.png' :
      'assets/images/blue-marker.jpeg',
      iconSize: [20, 28],
      iconAnchor: [10, 28],
      popupAnchor: [0, -28],
    });
  }

  private createVehicleIcon(): L.DivIcon {
    return L.divIcon({
      className: 'vehicle-marker',
      html: '<div class="vehicle-icon">🚌</div>',
      iconSize: [10, 20],
      iconAnchor: [5, 20],
    });
  }

  ngOnDestroy(): void {
    this.locationSubscription.unsubscribe();
    this.map?.remove();
  }
}
