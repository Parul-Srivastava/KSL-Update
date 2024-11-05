const functions = require('firebase-functions');
const admin = require('firebase-admin');
const express = require('express');
const tf = require('@tensorflow/tfjs-node'); // TensorFlow.js for Node.js
const { Readable } = require('stream');
const { promisify } = require('util');
const pipeline = promisify(require('stream').pipeline);
const sharp = require('sharp'); // For image processing

admin.initializeApp();

// Load the TFLite model
let model;
(async () => {
    model = await tf.loadLayersModel(model.tflite'); // Update with the correct path to your model
})();

const app = express();
app.use(express.json());
app.use(express.urlencoded({ extended: true }));

app.post('/predict', async (req, res) => {
    try {
        // Extract image from request
        const buffer = Buffer.from(req.body.image, 'base64'); // Assuming image is sent as base64
        
        // Preprocess image
        let imageTensor = await preprocessImage(buffer);
        
        // Make prediction
        const predictions = model.predict(imageTensor);
        const predictedIndex = predictions.argMax(-1).dataSync()[0];
        const labels = ["A", "D", "E"]; // Replace with your actual labels
        const predictedLabel = labels[predictedIndex];

        res.json({ prediction: predictedLabel });
    } catch (error) {
        console.error(error);
        res.status(500).send('Error during prediction');
    }
});

// Preprocess image function
async function preprocessImage(imageBuffer) {
    const image = sharp(imageBuffer)
        .resize(96, 96)
        .toBuffer();
    const imageTensor = tf.node.decodeImage(image, 3)
        .toFloat()
        .div(tf.scalar(255.0))
        .expandDims(0);
    return imageTensor;
}

exports.predict = functions.https.onRequest(app);
