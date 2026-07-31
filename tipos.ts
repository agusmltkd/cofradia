/**
 * Modelo de datos del núcleo.
 *
 * Idea central: un itinerario es UNA lista de puntos, y cada sección
 * (cruz de guía, paso de Cristo, paso de Virgen) recorre esos mismos
 * puntos con sus propios tiempos. El nombre del punto existe una sola vez.
 *
 * Y cada tramo entre dos puntos NO es un número suelto de minutos: es
 * `andar` (la chicotá, que apenas se comprime) + `paron` (la levantá,
 * que es de donde realmente se recupera un retraso).
 */

/** Minutos desde medianoche del día de salida. Puede pasar de 1440 (madrugá). */
export type Minutos = number;

export interface Punto {
  id: string;
  nombre: string;
  /**
   * Punto de horario fijo e innegociable. En Sevilla, el palquillo de la
   * Campana. En Málaga, la tribuna. Debe haber exactamente uno por plan.
   */
  ancla: boolean;
  /** Notas para el diputado (referencia visual, "azulejo del Cautivo", etc). */
  nota?: string;
}

export interface Tramo {
  /** Minutos andando entre el punto i y el i+1. */
  andar: Minutos;
  /** Minutos parado. Es el colchón del que se tira para recuperar retraso. */
  paron: Minutos;
  /**
   * true si el reparto andar/parón se ha estimado automáticamente al
   * importar y todavía no lo ha revisado nadie que sepa de verdad.
   */
  estimado?: boolean;
}

export interface Seccion {
  id: string;
  nombre: string;
  /** Hora de paso por el primer punto. */
  salida: Minutos;
  /** Longitud = puntos.length - 1. */
  tramos: Tramo[];
}

export interface Plan {
  id: string;
  nombre: string;
  hermandadId: string;
  puntos: Punto[];
  secciones: Seccion[];
}

export interface OpcionesRecalculo {
  /** Minutos que se respetan siempre en cada parón. Por defecto 1. */
  paronMinimo?: Minutos;
  /**
   * Aceleración máxima admisible del tiempo de andar, en fracción.
   * Por encima de esto el plan se marca como NO VIABLE: no se le puede
   * pedir a los costaleros que anden un 20% más rápido tres horas seguidas.
   * Por defecto 0.08 (8%).
   */
  aceleracionAndarMaxima?: number;
  /** Aviso si la separación entre secciones se desvía más de esto. Por defecto 3. */
  derivaCortejoMaxima?: Minutos;
}

export interface HorarioPunto {
  puntoId: string;
  nombre: string;
  ancla: boolean;
  /** Hora original planificada. */
  original: Minutos;
  /** Hora recalculada. */
  nueva: Minutos;
  /** nueva - original. Positivo = vas más tarde que el plan original. */
  delta: Minutos;
}

export interface ResultadoSeccion {
  seccionId: string;
  nombre: string;
  /**
   * false si para llegar al ancla haría falta acelerar el andar por encima
   * del máximo. Cuando esto es false hay que avisar al Consejo, no correr.
   */
  viable: boolean;
  nuevaSalida: Minutos;
  horarios: HorarioPunto[];
  /** Minutos que hay que recuperar antes del ancla (= el retraso). */
  recorteNecesario: Minutos;
  /** De cuántos salen de los parones. */
  recorteDeParones: Minutos;
  /** Y cuántos hay que sacar acelerando el andar. Idealmente 0. */
  recorteDeAndar: Minutos;
  /** Fracción de aceleración del andar. 0.05 = hay que andar un 5% más rápido. */
  aceleracionAndar: number;
  /** Minutos de parón que todavía quedan libres después de este recorte. */
  margenRestante: Minutos;
  avisos: string[];
}

export interface ResultadoPlan {
  planId: string;
  retraso: Minutos;
  viable: boolean;
  secciones: ResultadoSeccion[];
  /** Avisos sobre cómo se estira o encoge la separación entre pasos. */
  avisosCortejo: string[];
}
