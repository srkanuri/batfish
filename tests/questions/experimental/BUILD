load("@batfish//skylark:ref_tests.bzl", "ref_tests")

ref_tests(
    name = "ref_tests",
    commands = ":commands",
    extra_deps = glob(["*.ref"]) + [
        "//questions:experimental",
        "//test_rigs",
    ],
)
