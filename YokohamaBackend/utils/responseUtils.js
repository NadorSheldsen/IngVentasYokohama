// Helper utilities for sending HTTP responses in a user-friendly way
// Always log the technical error on the server, but send only a
// concise, user-friendly message to the client.
module.exports.handleServerError = function (res, userMessage, error, status = 500) {
  // Log full error server-side for debugging
  try {
    console.error(error);
  } catch (logErr) {
    console.error('Error while logging error:', logErr);
  }

  // By default do not leak technical details to clients. When
  // DEBUG_ERRORS env var is set to 'true', include the error.message
  // to help debugging in non-production environments.
  const debug = process.env.DEBUG_ERRORS === 'true';
  const payload = { message: userMessage };
  if (debug && error) {
    payload.error = error.message || String(error);
  }

  try {
    return res.status(status).json(payload);
  } catch (sendErr) {
    console.error('Failed to send error response:', sendErr);
    return res.status(500).json({ message: 'Ocurrió un error en el servidor' });
  }
};
