// Serve the test fixtures over HTTP so the browser test run can read them with FetchLoader,
// at /base/kotlin/<resource path>.
config.files.push({
    pattern: 'kotlin/GH-36/*.json',
    included: false,
    served: true,
    watched: false,
});
