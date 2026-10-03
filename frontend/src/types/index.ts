export interface HealthStatus {
  status: string;
  message: string;
  timestamp: string;
}

export interface ApiError {
  type: string;
  title: string;
  status: number;
  detail: string;
  timestamp?: string;
  invalidFields?: Record<string, string>;
}
