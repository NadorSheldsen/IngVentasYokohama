const sharp = require('sharp');

/**
 * Procesa una imagen (redimensiona y comprime) para normalizar tamaño
 * @param {Buffer|String} imageData - Buffer de imagen o string base64 de la imagen
 * @param {Object} options - Opciones de procesamiento
 * @param {number} options.maxWidth - Ancho máximo (default: 800)
 * @param {number} options.maxHeight - Alto máximo (default: 600)
 * @param {number} options.quality - Calidad JPEG (default: 80, rango: 0-100)
 * @returns {Promise<Buffer>} Imagen procesada como Buffer
 */
async function processImage(imageData, options = {}) {
  const {
    maxWidth = 800,
    maxHeight = 600,
    quality = 80
  } = options;

  try {
    // Si es string base64, convertir a Buffer
    let buffer = imageData;
    if (typeof imageData === 'string') {
      // Remover prefijo data:image si existe
      const base64String = imageData.replace(/^data:image\/[^;]+;base64,/, '');
      buffer = Buffer.from(base64String, 'base64');
    }

    // Procesar imagen: redimensionar manteniendo aspect ratio y comprimir
    const processedBuffer = await sharp(buffer)
      .resize(maxWidth, maxHeight, {
        fit: 'inside',      // Mantiene aspect ratio, cabe dentro de max dimensions
        withoutEnlargement: true // No agranda imágenes más pequeñas
      })
      .jpeg({ quality, progressive: true })  // Convertir a JPEG con progresión
      .toBuffer();

    return processedBuffer;
  } catch (error) {
    console.error('[imageProcessor] Error procesando imagen:', error.message);
    throw new Error(`Error al procesar imagen: ${error.message}`);
  }
}

/**
 * Procesa una imagen y devuelve como base64 (para almacenar en BD)
 * @param {Buffer|String} imageData - Buffer o string base64 de la imagen
 * @param {Object} options - Opciones de procesamiento
 * @returns {Promise<String>} Imagen procesada como base64
 */
async function processImageToBase64(imageData, options = {}) {
  try {
    const processedBuffer = await processImage(imageData, options);
    return processedBuffer.toString('base64');
  } catch (error) {
    console.error('[imageProcessor] Error en processImageToBase64:', error.message);
    throw error;
  }
}

/**
 * Procesa una imagen y fuerza un maximo de longitud base64.
 * Si no cabe, reduce calidad y dimensiones de forma iterativa.
 */
async function processImageToBase64WithinLimit(imageData, options = {}) {
  const {
    maxBase64Length = 60000,
    maxWidth = 800,
    maxHeight = 600,
    quality = 80,
    minQuality = 35,
    minWidth = 240,
    minHeight = 180,
    maxAttempts = 12
  } = options;

  let sourceBuffer = imageData;
  if (typeof imageData === 'string') {
    const base64String = imageData.replace(/^data:image\/[^;]+;base64,/, '');
    sourceBuffer = Buffer.from(base64String, 'base64');
  }

  let targetWidth = maxWidth;
  let targetHeight = maxHeight;
  let targetQuality = quality;
  let lastLength = 0;

  for (let attempt = 1; attempt <= maxAttempts; attempt += 1) {
    const processedBuffer = await sharp(sourceBuffer)
      .resize(targetWidth, targetHeight, {
        fit: 'inside',
        withoutEnlargement: true
      })
      .jpeg({ quality: targetQuality, progressive: true })
      .toBuffer();

    const b64 = processedBuffer.toString('base64');
    lastLength = b64.length;

    if (b64.length <= maxBase64Length) {
      return b64;
    }

    if (targetQuality > minQuality) {
      targetQuality = Math.max(minQuality, targetQuality - 10);
      continue;
    }

    const nextWidth = Math.max(minWidth, Math.floor(targetWidth * 0.8));
    const nextHeight = Math.max(minHeight, Math.floor(targetHeight * 0.8));
    const sizeChanged = nextWidth !== targetWidth || nextHeight !== targetHeight;

    targetWidth = nextWidth;
    targetHeight = nextHeight;
    targetQuality = quality;

    if (!sizeChanged && targetQuality <= minQuality) {
      break;
    }
  }

  throw new Error(`No se pudo comprimir la imagen por debajo del limite (${maxBase64Length}). Ultimo tamano: ${lastLength}`);
}

/**
 * Procesa una imagen y devuelve el tamaño en bytes
 * @param {Buffer|String} imageData - Buffer o string base64 de la imagen
 * @param {Object} options - Opciones de procesamiento
 * @returns {Promise<number>} Tamaño en bytes
 */
async function getProcessedImageSize(imageData, options = {}) {
  try {
    const processedBuffer = await processImage(imageData, options);
    return processedBuffer.length;
  } catch (error) {
    console.error('[imageProcessor] Error en getProcessedImageSize:', error.message);
    throw error;
  }
}

function looksLikeBase64(value) {
  if (typeof value !== 'string') return false;
  const cleaned = value.replace(/\s+/g, '');
  if (!cleaned || cleaned.length < 64) return false;
  if (cleaned.length % 4 !== 0) return false;
  return /^[A-Za-z0-9+/=]+$/.test(cleaned);
}

/**
 * Prepara un campo de imagen para persistir en BD.
 * - null/undefined/'' => null
 * - Buffer/base64/dataURI => base64 procesado (redimensionado/comprimido)
 * - otros strings (por ejemplo URL) => se regresan sin cambios
 */
async function processImageForStorage(value, options = {}) {
  const {
    maxBase64Length = 60000,
    ...imageOptions
  } = options;

  if (value === null || value === undefined) return null;

  if (typeof value === 'string') {
    const trimmed = value.trim();
    if (!trimmed) return null;

    const isDataUri = /^data:image\//i.test(trimmed);
    const isB64 = looksLikeBase64(trimmed);
    if (!isDataUri && !isB64) {
      return value;
    }
  }

  return processImageToBase64WithinLimit(value, {
    maxBase64Length,
    ...imageOptions
  });
}

module.exports = {
  processImage,
  processImageToBase64,
  processImageToBase64WithinLimit,
  getProcessedImageSize,
  processImageForStorage
};
