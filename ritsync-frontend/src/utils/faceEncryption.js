/**
 * ZKP-Inspired Face Descriptor Encryption
 * 
 * SECURITY MODEL:
 * 1. AES-256-GCM encryption of face descriptors
 * 2. PBKDF2 key derivation (100k iterations)
 * 3. Client-side only - server never sees raw data
 * 4. Random IV for each encryption
 * 
 * PERFORMANCE:
 * - Web Crypto API (native, fast)
 * - Minimal memory footprint
 * - Base64 encoding for JSON transport
 */

const ITERATIONS = 100000;
const KEY_LENGTH = 256;
const IV_LENGTH = 12; // 12 bytes for AES-GCM
const SALT_LENGTH = 16;

/**
 * Derive encryption key from user password using PBKDF2
 */
const deriveKey = async (password, salt) => {
    const encoder = new TextEncoder();
    const keyMaterial = await crypto.subtle.importKey(
        'raw',
        encoder.encode(password),
        'PBKDF2',
        false,
        ['deriveKey']
    );

    return crypto.subtle.deriveKey(
        {
            name: 'PBKDF2',
            salt: salt || crypto.getRandomValues(new Uint8Array(SALT_LENGTH)),
            iterations: ITERATIONS,
            hash: 'SHA-256'
        },
        keyMaterial,
        { name: 'AES-GCM', length: KEY_LENGTH },
        false,
        ['encrypt', 'decrypt']
    );
};

/**
 * Encrypt face descriptor with AES-256-GCM
 * @param {number[]} descriptor - 128-dimensional face descriptor array
 * @param {string} password - User's password (or derived secret)
 * @returns {Promise<{encryptedDescriptor: string, encryptionIv: string, salt: string}>}
 */
export const encryptDescriptor = async (descriptor, password) => {
    const encoder = new TextEncoder();
    const salt = crypto.getRandomValues(new Uint8Array(SALT_LENGTH));
    const iv = crypto.getRandomValues(new Uint8Array(IV_LENGTH));
    const key = await deriveKey(password, salt);

    const descriptorStr = JSON.stringify(descriptor);
    const encrypted = await crypto.subtle.encrypt(
        { name: 'AES-GCM', iv },
        key,
        encoder.encode(descriptorStr)
    );

    return {
        encryptedDescriptor: btoa(String.fromCharCode(...new Uint8Array(encrypted))),
        encryptionIv: btoa(String.fromCharCode(...iv)),
        salt: btoa(String.fromCharCode(...salt))
    };
};

/**
 * Decrypt face descriptor
 * @param {string} encryptedDescriptor - Base64 encoded encrypted data
 * @param {string} encryptionIv - Base64 encoded IV
 * @param {string} salt - Base64 encoded salt
 * @param {string} password - User's password
 * @returns {Promise<number[]>} Decrypted face descriptor
 */
export const decryptDescriptor = async (encryptedDescriptor, encryptionIv, salt, password) => {
    const decodeBase64 = (str) => Uint8Array.from(atob(str), c => c.charCodeAt(0));
    
    const encrypted = decodeBase64(encryptedDescriptor);
    const iv = decodeBase64(encryptionIv);
    const saltBytes = decodeBase64(salt);
    
    const key = await deriveKey(password, saltBytes);
    
    const decrypted = await crypto.subtle.decrypt(
        { name: 'AES-GCM', iv },
        key,
        encrypted
    );

    const decoder = new TextDecoder();
    return JSON.parse(decoder.decode(decrypted));
};

/**
 * Generate HMAC for ZKP response
 */
export const generateHMAC = async (challenge, booleanResult, password) => {
    const encoder = new TextEncoder();
    const data = `${challenge}|${booleanResult}`;
    
    const key = await crypto.subtle.importKey(
        'raw',
        encoder.encode(password),
        { name: 'HMAC', hash: 'SHA-256' },
        false,
        ['sign']
    );
    
    const signature = await crypto.subtle.sign('HMAC', key, encoder.encode(data));
    return btoa(String.fromCharCode(...new Uint8Array(signature)));
};

/**
 * Compare two face descriptors using Euclidean distance
 * @param {number[]} desc1 - First descriptor
 * @param {number[]} desc2 - Second descriptor
 * @returns {number} Distance (lower = more similar)
 */
export const compareDescriptors = (desc1, desc2) => {
    if (!desc1 || !desc2 || desc1.length !== desc2.length) return 999;
    let sum = 0;
    for (let i = 0; i < desc1.length; i++) {
        sum += Math.pow(desc1[i] - desc2[i], 2);
    }
    return Math.sqrt(sum);
};
