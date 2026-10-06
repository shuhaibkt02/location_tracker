/// Historical daily travel distance record.
class DailyDistance {
  /// Date key in ISO format (YYYY-MM-DD).
  final String date;

  /// Total accumulated traveling distance in meters for the day.
  final double distanceMeters;

  const DailyDistance({
    required this.date,
    required this.distanceMeters,
  });

  factory DailyDistance.fromMap(Map<dynamic, dynamic> map) {
    return DailyDistance(
      date: map['date'] as String? ?? '',
      distanceMeters: (map['distance'] as num?)?.toDouble() ?? 0.0,
    );
  }

  Map<String, dynamic> toMap() {
    return {
      'date': date,
      'distance': distanceMeters,
    };
  }

  @override
  String toString() =>
      'DailyDistance(date: $date, distance: ${(distanceMeters / 1000.0).toStringAsFixed(2)} km)';
}
