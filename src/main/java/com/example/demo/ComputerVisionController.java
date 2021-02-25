package com.example.demo;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.microsoft.azure.cognitiveservices.vision.computervision.ComputerVision;
import com.microsoft.azure.cognitiveservices.vision.computervision.ComputerVisionClient;
import com.microsoft.azure.cognitiveservices.vision.computervision.ComputerVisionManager;
import com.microsoft.azure.cognitiveservices.vision.computervision.implementation.ComputerVisionImpl;
import com.microsoft.azure.cognitiveservices.vision.computervision.models.Category;
import com.microsoft.azure.cognitiveservices.vision.computervision.models.CelebritiesModel;
import com.microsoft.azure.cognitiveservices.vision.computervision.models.FaceDescription;
import com.microsoft.azure.cognitiveservices.vision.computervision.models.ImageAnalysis;
import com.microsoft.azure.cognitiveservices.vision.computervision.models.ImageCaption;
import com.microsoft.azure.cognitiveservices.vision.computervision.models.ImageTag;
import com.microsoft.azure.cognitiveservices.vision.computervision.models.LandmarksModel;
import com.microsoft.azure.cognitiveservices.vision.computervision.models.Line;
import com.microsoft.azure.cognitiveservices.vision.computervision.models.OcrDetectionLanguage;
import com.microsoft.azure.cognitiveservices.vision.computervision.models.OperationStatusCodes;
import com.microsoft.azure.cognitiveservices.vision.computervision.models.ReadInStreamHeaders;
import com.microsoft.azure.cognitiveservices.vision.computervision.models.ReadOperationResult;
import com.microsoft.azure.cognitiveservices.vision.computervision.models.ReadResult;
import com.microsoft.azure.cognitiveservices.vision.computervision.models.VisualFeatureTypes;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/vision")
public class ComputerVisionController {

	@Value("${azure.subscription-key}")
	private String subscriptionKey;

	@Value("${azure.cognitive-service-endpoint}")
	private String endpoint;

	@Operation(summary = "Computer vision does this file have a model", description = "Computer vision does this file have a model")
	@PostMapping("/modelImage")
	public ResponseEntity<String> isModelImage(
			@Parameter(description = "path of image to analyse") @RequestParam(value = "imagePath", defaultValue = "src\\main\\resources\\images\\model_front.jpg") String imagePath)
			throws IOException {

		log.info("Azure Cognitive Services Computer Vision - Java Quickstart Sample");

		// Create an authenticated Computer Vision client.
		ComputerVisionClient compVisClient = authenticate(subscriptionKey, endpoint);

		// Analyze local and remote images
		ImageAnalysis imageAnalysis = analyzeLocalImage(compVisClient, imagePath);

		// Check for category "people"
		// taxonomy defined here: https://docs.microsoft.com/en-us/azure/cognitive-services/computer-vision/category-taxonomy
		List<Category> peopleCategories = imageAnalysis.categories().stream().filter(c -> c.name().toLowerCase().contains("people")).collect(Collectors.toList());
		if (peopleCategories.isEmpty()) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body("No people categories found");
		}

		return ResponseEntity.status(HttpStatus.OK).body("People categories found");

	}

	@Operation(summary = "Computer vision analyse image file", description = "Computer vision analyse image file")
	@PostMapping("/analyse")
	public ResponseEntity<ImageAnalysis> analyse(
			@Parameter(description = "path of image to analyse") @RequestParam(value = "imagePath", defaultValue = "src\\main\\resources\\images\\model_front.jpg") String imagePath)
			throws IOException {

		log.info("Azure Cognitive Services Computer Vision - Java Quickstart Sample");

		// Create an authenticated Computer Vision client.
		ComputerVisionClient compVisClient = authenticate(subscriptionKey, endpoint);

		// Analyze local and remote images
		ImageAnalysis imageAnalysis = analyzeLocalImage(compVisClient, imagePath);

		return ResponseEntity.status(HttpStatus.OK).body(imageAnalysis);

	}

	@Operation(summary = "Computer vision read text file", description = "Computer vision analyse image reading text")
	@PostMapping("/readtext")
	public ResponseEntity<String> readText(
			@Parameter(description = "path of image to analyse") @RequestParam(value = "imagePath", defaultValue = "src\\main\\resources\\images\\model_front.jpg") String imagePath)
			throws IOException {

		log.info("Azure Cognitive Services Computer Vision - Java Quickstart Sample");

		// Create an authenticated Computer Vision client.
		ComputerVisionClient compVisClient = authenticate(subscriptionKey, endpoint);

		// Read from local file
		StringBuilder builder = readFromFile(compVisClient, imagePath);

		return ResponseEntity.ok("Response: " + builder.toString());
	}

	public ComputerVisionClient authenticate(String subscriptionKey, String endpoint) {
		return ComputerVisionManager.authenticate(subscriptionKey).withEndpoint(endpoint);
	}

	@ExceptionHandler(IOException.class)
	public ResponseEntity<?> handleFileIOException(IOException exc) {
		log.error("IOException: {}", exc.getMessage(), exc);
		return ResponseEntity.notFound().build();
	}

	/*
	 * Analyze a local image:
	 *
	 * Set a string variable equal to the path of a local image. The image path below is a relative path.
	 */
	public ImageAnalysis analyzeLocalImage(ComputerVisionClient compVisClient, String imagePath) {

		// This list defines the features to be extracted from the image.
		List<VisualFeatureTypes> featuresToExtractFromLocalImage = new ArrayList<>();
		featuresToExtractFromLocalImage.add(VisualFeatureTypes.DESCRIPTION);
		featuresToExtractFromLocalImage.add(VisualFeatureTypes.CATEGORIES);
		featuresToExtractFromLocalImage.add(VisualFeatureTypes.TAGS);
		featuresToExtractFromLocalImage.add(VisualFeatureTypes.FACES);
		featuresToExtractFromLocalImage.add(VisualFeatureTypes.ADULT);
		featuresToExtractFromLocalImage.add(VisualFeatureTypes.COLOR);
		featuresToExtractFromLocalImage.add(VisualFeatureTypes.IMAGE_TYPE);
		log.info("Analyzing local image ...");

		// Known issue on an illegal reflective access
		// https://github.com/Azure/autorest-clientruntime-for-java/issues/569

		ImageAnalysis analysis = null;

		try {
			// Need a byte array for analyzing a local image.
			File rawImage = new File(imagePath);
			byte[] imageByteArray = Files.readAllBytes(rawImage.toPath());

			// Call the Computer Vision service and tell it to analyze the loaded image.
			analysis = compVisClient.computerVision().analyzeImageInStream().withImage(imageByteArray).withVisualFeatures(featuresToExtractFromLocalImage).execute();

			// Display image captions and confidence values.
			log.info("Captions: ");
			for (ImageCaption caption : analysis.description().captions()) {
				log.info(String.format("\'%s\' with confidence %f\n", caption.text(), caption.confidence()));
			}
			// Display image category names and confidence values.
			log.info("Categories: ");
			for (Category category : analysis.categories()) {
				log.info(String.format("\'%s\' with confidence %f\n", category.name(), category.score()));
			}
			// Display image tags and confidence values.
			log.info("Tags: ");
			for (ImageTag tag : analysis.tags()) {
				log.info(String.format("\'%s\' with confidence %f\n", tag.name(), tag.confidence()));
			}
			// Display any faces found in the image and their location.
			log.info("Faces: ");
			for (FaceDescription face : analysis.faces()) {
				log.info(String.format("\'%s\' of age %d at location (%d, %d), (%d, %d)\n", face.gender(), face.age(), face.faceRectangle().left(), face.faceRectangle().top(),
						face.faceRectangle().left() + face.faceRectangle().width(), face.faceRectangle().top() + face.faceRectangle().height()));
			}
			// Display whether any adult or racy content was detected and the confidence
			// values.
			log.info("Adult: ");
			log.info(String.format("Is adult content: %b with confidence %f\n", analysis.adult().isAdultContent(), analysis.adult().adultScore()));
			log.info(String.format("Has racy content: %b with confidence %f\n", analysis.adult().isRacyContent(), analysis.adult().racyScore()));
			// Display the image color scheme.
			log.info("Color scheme: ");
			log.info("Is black and white: " + analysis.color().isBWImg());
			log.info("Accent color: " + analysis.color().accentColor());
			log.info("Dominant background color: " + analysis.color().dominantColorBackground());
			log.info("Dominant foreground color: " + analysis.color().dominantColorForeground());
			log.info("Dominant colors: " + String.join(", ", analysis.color().dominantColors()));
			// Display any celebrities detected in the image and their locations.
			log.info("Celebrities: ");
			for (Category category : analysis.categories()) {
				if (category.detail() != null && category.detail().celebrities() != null) {
					for (CelebritiesModel celeb : category.detail().celebrities()) {
						log.info(String.format("\'%s\' with confidence %f at location (%d, %d), (%d, %d)\n", celeb.name(), celeb.confidence(), celeb.faceRectangle().left(),
								celeb.faceRectangle().top(), celeb.faceRectangle().left() + celeb.faceRectangle().width(),
								celeb.faceRectangle().top() + celeb.faceRectangle().height()));
					}
				}
			}
			// Display any landmarks detected in the image and their locations.
			log.info("Landmarks: ");
			for (Category category : analysis.categories()) {
				if (category.detail() != null && category.detail().landmarks() != null) {
					for (LandmarksModel landmark : category.detail().landmarks()) {
						log.info(String.format("\'%s\' with confidence %f\n", landmark.name(), landmark.confidence()));
					}
				}
			}
			// Display what type of clip art or line drawing the image is.
			log.info("Image type:");
			log.info("Clip art type: " + analysis.imageType().clipArtType());
			log.info("Line drawing type: " + analysis.imageType().lineDrawingType());
		}

		catch (Exception e) {
			log.error("Exception", e);
		}

		return analysis;
	}
	// END - Analyze a local image.

	/**
	 * READ : Performs a Read Operation on a local image
	 * 
	 * @param client        instantiated vision client
	 * @param localFilePath local file path from which to perform the read operation against
	 * @return
	 * @return
	 */
	private StringBuilder readFromFile(ComputerVisionClient client, String imagePath) {

		log.info("-----------------------------------------------");

		log.info("Read with local file: " + imagePath);

		try {
			File rawImage = new File(imagePath);
			byte[] localImageBytes = Files.readAllBytes(rawImage.toPath());

			// Cast Computer Vision to its implementation to expose the required methods
			ComputerVisionImpl vision = (ComputerVisionImpl) client.computerVision();

			// Read in remote image and response header
			ReadInStreamHeaders responseHeader = vision.readInStreamWithServiceResponseAsync(localImageBytes, OcrDetectionLanguage.FR).toBlocking().single().headers();

			// Extract the operationLocation from the response header
			String operationLocation = responseHeader.operationLocation();
			log.info("Operation Location:" + operationLocation);

			return getAndPrintReadResult(vision, operationLocation);

		} catch (Exception e) {
			log.info(e.getMessage());
			log.error("Exception", e);
		}
		return null;
	}

	/**
	 * Extracts the OperationId from a Operation-Location returned by the POST Read operation
	 * 
	 * @param operationLocation
	 * @return operationId
	 */
	private String extractOperationIdFromOpLocation(String operationLocation) {
		if (operationLocation != null && !operationLocation.isEmpty()) {
			String[] splits = operationLocation.split("/");

			if (splits != null && splits.length > 0) {
				return splits[splits.length - 1];
			}
		}
		throw new IllegalStateException("Something went wrong: Couldn't extract the operation id from the operation location");
	}

	/**
	 * Polls for Read result and prints results to console
	 * 
	 * @param vision Computer Vision instance
	 * @return
	 * @return
	 * @return operationLocation returned in the POST Read response header
	 */
	private StringBuilder getAndPrintReadResult(ComputerVision vision, String operationLocation) throws InterruptedException {

		log.info("Polling for Read results ...");

		// Extract OperationId from Operation Location
		String operationId = extractOperationIdFromOpLocation(operationLocation);

		boolean pollForResult = true;
		ReadOperationResult readResults = null;
		StringBuilder builder = new StringBuilder();

		while (pollForResult) {
			// Poll for result every second
			Thread.sleep(1000);
			readResults = vision.getReadResult(UUID.fromString(operationId));

			// The results will no longer be null when the service has finished processing the request.
			if (readResults != null) {
				// Get request status
				OperationStatusCodes status = readResults.status();

				if (status == OperationStatusCodes.FAILED || status == OperationStatusCodes.SUCCEEDED) {
					pollForResult = false;
				}
			}
		}
		// Print read results, page per page
		for (ReadResult pageResult : readResults.analyzeResult().readResults()) {
			log.info("");
			log.info("Printing Read results for page " + pageResult.page());

			for (Line line : pageResult.lines()) {
				builder.append(line.text());
			}

			log.info(builder.toString());
		}

		return builder;

	}

}