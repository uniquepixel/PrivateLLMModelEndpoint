package privatellm;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for extracting player tags from Clash Royale profile images using LM
 * Studio
 */
public class VisionService {
	private static final int CONNECT_TIMEOUT = 30000; // 30 seconds
	private static final int READ_TIMEOUT = 120000; // 120 seconds for image processing
	private static final Pattern PLAYER_TAG_PATTERN = Pattern.compile("#[A-Z0-9]{3,10}");

	private static final String PROMPT = """
			Extrahiere den Spielertag aus dem folgenden Clash-Royale-Profil-Screenshot fehlerfrei, auch bei schlechter Bildqualität.
			Es kann sein, dass der Tag garnicht zu sehen ist. Falls es nicht auf diesem Bild zu sehen ist, gib mir unbedingt "NOTAG" zurück.
			Dinge wie "Clankriegsveteran" zählen nicht als Tag und sollen somit ignoriert werden.
			Aufgabe:
			Finde im Bild das Textfeld mit dem Spielertag.
			Der Spielertag steht im Profilbereich unter dem Spielernamen und beginnt immer mit # (Beispiel: #2YLJPV0LQ). Gib dieses # Zeichen unbedingt mit aus
			Gib ausschließlich den erkannten Spielertag als Text oder "NOTAG" aus, ohne Zusatz, ohne Erklärung, ohne Anführungszeichen.
			Qualitätsanforderungen:
			Nutze alle verfügbaren Techniken zur Texterkennung (OCR, Vergrößerung, Schärfung, Rauschunterdrückung), um auch bei Unschärfe oder Kompression den Text korrekt zu lesen.
			Wenn einzelne Zeichen unscharf sind, wähle das wahrscheinlichste Zeichen basierend auf:
			dem offiziellen Format von Clash-Royale-Tags (Großbuchstaben A–Z und Ziffern 0–9, beginnend mit #, kein O),
			typischen Verwechslungen (z.B. 0 vs. Q, 1 vs. I, 8 vs. B, **Y vs. V**) und ihrer Form im Bild.
			Vergleiche das Ergebnis mit gültigen Tag-Mustern und korrigiere offensichtliche OCR-Fehler.
			Fehlersicherheit:
			Es ist unglaublich wichtig, dass dieses Ergebnis richtig ist und es ist nicht wichtig, wie lange du dafür brauchst.
						""";

	private final String lmStudioEndpoint;

	public VisionService(String lmStudioEndpoint) {
		this.lmStudioEndpoint = lmStudioEndpoint;
	}

	/**
	 * Extract player tag from list of image URLs
	 * 
	 * @return Player tag (e.g., "#ABC123") or null if not found
	 */
	public String extractPlayerTag(List<String> imageUrls) throws IOException {
		if (imageUrls == null || imageUrls.isEmpty()) {
			return null;
		}

		// Download all images
		System.out.println("  Downloading " + imageUrls.size() + " image(s)...");
		List<byte[]> images = new ArrayList<>();
		for (String url : imageUrls) {
			try {
				byte[] imageData = downloadImage(url);
				if (imageData != null) {
					images.add(imageData);
				}
			} catch (IOException e) {
				System.err.println("    Error downloading image from " + url + ": " + e.getMessage());
			}
		}

		if (images.isEmpty()) {
			System.err.println("  Failed to download any images");
			return null;
		}

		System.out.println("  ✓ Downloaded " + images.size() + " image(s)");

		// Call LM Studio for analysis
		System.out.println("  Analyzing images with LM Studio...");
		String response = callLMStudio(images);

		// Parse player tag from response
		String playerTag = parsePlayerTag(response);
		return playerTag;
	}

	/**
	 * Download image from URL to byte array
	 */
	private byte[] downloadImage(String imageUrl) throws IOException {
		URL url = new URL(imageUrl);
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();

		conn.setRequestMethod("GET");
		conn.setConnectTimeout(10000);
		conn.setReadTimeout(30000);

		int responseCode = conn.getResponseCode();
		if (responseCode != 200) {
			throw new IOException("Failed to download image, status code: " + responseCode);
		}

		try (InputStream is = conn.getInputStream(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
			byte[] buffer = new byte[8192];
			int bytesRead;
			while ((bytesRead = is.read(buffer)) != -1) {
				baos.write(buffer, 0, bytesRead);
			}
			return baos.toByteArray();
		}
	}

	/**
	 * Call LM Studio with images for analysis
	 */
	private String callLMStudio(List<byte[]> images) throws IOException {
		URL url = new URL(lmStudioEndpoint);
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();

		conn.setRequestMethod("POST");
		conn.setRequestProperty("Content-Type", "application/json");
		conn.setDoOutput(true);
		conn.setConnectTimeout(CONNECT_TIMEOUT);
		conn.setReadTimeout(READ_TIMEOUT);

		// Build request in OpenAI vision format
		JSONObject request = new JSONObject();
		request.put("model", "vision-model");
		request.put("max_tokens", 100);

		JSONArray messages = new JSONArray();
		JSONObject message = new JSONObject();
		message.put("role", "user");

		JSONArray content = new JSONArray();

		// Add text prompt
		JSONObject textPart = new JSONObject();
		textPart.put("type", "text");
		textPart.put("text", PROMPT);
		content.put(textPart);

		// Add images as base64
		for (byte[] imageData : images) {
			String base64Image = Base64.getEncoder().encodeToString(imageData);
			JSONObject imagePart = new JSONObject();
			imagePart.put("type", "image_url");

			JSONObject imageUrl = new JSONObject();
			imageUrl.put("url", "data:image/jpeg;base64," + base64Image);
			imagePart.put("image_url", imageUrl);

			content.put(imagePart);
		}

		message.put("content", content);
		messages.put(message);
		request.put("messages", messages);

		// Send request
		try (OutputStream os = conn.getOutputStream()) {
			byte[] input = request.toString().getBytes(StandardCharsets.UTF_8);
			os.write(input, 0, input.length);
		}

		// Read response
		int responseCode = conn.getResponseCode();
		if (responseCode != 200) {
			String errorBody = "";
			try (BufferedReader br = new BufferedReader(
					new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
				StringBuilder error = new StringBuilder();
				String line;
				while ((line = br.readLine()) != null) {
					error.append(line.trim());
				}
				errorBody = error.toString();
			} catch (Exception e) {
				// Ignore
			}
			throw new IOException("LM Studio returned error code: " + responseCode
					+ (errorBody.isEmpty() ? "" : ", body: " + errorBody));
		}

		try (BufferedReader br = new BufferedReader(
				new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
			StringBuilder response = new StringBuilder();
			String line;
			while ((line = br.readLine()) != null) {
				response.append(line.trim());
			}

			String responseStr = response.toString();

			// Parse OpenAI response format
			JSONObject responseJson = new JSONObject(responseStr);
			if (responseJson.has("choices")) {
				JSONArray choices = responseJson.getJSONArray("choices");
				if (choices.length() > 0) {
					JSONObject choice = choices.getJSONObject(0);
					if (choice.has("message")) {
						JSONObject msg = choice.getJSONObject("message");
						if (msg.has("content")) {
							return msg.getString("content");
						}
					}
				}
			}

			return responseStr;
		}
	}

	/**
	 * Parse player tag from LM Studio response using regex
	 */
	private String parsePlayerTag(String response) {
		if (response == null || response.isEmpty()) {
			return null;
		}

		// Check if response indicates not found
		if (response.toUpperCase().contains("NOTAG")) {
			return "NOTAG";
		}
		Set<Character> allowed = Set.of('#', '0', '2', '8', '9', 'P', 'Y', 'L', 'Q', 'G', 'R', 'J', 'C', 'U', 'V', 'O'); // Beispiel

		Matcher matcher = PLAYER_TAG_PATTERN.matcher(response);
		if (matcher.find()) { //finde etwas mit #
			String tag = matcher.group(); //wähle das aus
			try {
				Integer.parseInt(tag.replace("#", "")); //versuch das mal zu ner Zahl zu machen
				return "NOTAG"; //wenns klappt, nicht gut, skip
			} catch (Exception e) { //wenns nicht klappt, gut
				boolean onlyAllowed = tag.chars().allMatch(ch -> allowed.contains((char) ch)); //prüfen, ob nur erlaubte Zeichen drin sind
				boolean hasForbidden = !onlyAllowed; //umdrehen -> prüfen, ob unerlaubte Zeichen drin sind
				if (hasForbidden) { //sind welche drin?
					return "NOTAG"; //wenn ja, skip, kein richtiger Tag
				}
				return tag; //ansonsten passt, gib den Tag zurück
			}
		}

		return "NOTAG";
	}
}
